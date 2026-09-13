package org.opensources.courses.feature.homeassistant.data.sync

import org.opensources.courses.core.sync.SyncQueue
import org.opensources.courses.feature.catalog.domain.TextNormalizer
import org.opensources.courses.feature.homeassistant.domain.ConflictResolver
import org.opensources.courses.feature.homeassistant.domain.HaTodoItem
import org.opensources.courses.feature.homeassistant.domain.ItemDescriptionCodec
import org.opensources.courses.feature.homeassistant.domain.ItemQuantity
import org.opensources.courses.feature.homeassistant.domain.ItemResolution
import org.opensources.courses.feature.homeassistant.domain.LocalItemState
import org.opensources.courses.feature.homeassistant.domain.RemoteItemState

/** Applies the remote state of one list to Room, following [ConflictResolver]. */
class HaItemReconciler(
    private val store: SyncLocalStore,
    private val queue: SyncQueue,
) {
    /**
     * Local items without a remote id take the uid of an unclaimed remote item with the same
     * name. Used when a list is linked to a non-empty Home Assistant list.
     */
    suspend fun adoptByName(
        listLocalId: String,
        remoteItems: List<HaTodoItem>,
    ) {
        val locals = store.items(listLocalId)
        val claimed = locals.mapNotNull { it.remoteId }.toMutableSet()
        locals.filter { it.remoteId == null && !it.isDeleted }.forEach { local ->
            val key = TextNormalizer.normalize(local.name)
            remoteItems.firstOrNull { it.uid !in claimed && TextNormalizer.normalize(it.summary) == key }?.let { match ->
                claimed += match.uid
                store.setItemRemoteId(local.localId, match.uid)
            }
        }
    }

    suspend fun reconcile(
        listLocalId: String,
        remoteItems: List<HaTodoItem>,
        supportsDescription: Boolean,
    ) {
        val locals = store.items(listLocalId)
        val remoteByUid = remoteItems.associateBy { it.uid }
        for (local in locals) {
            val remoteId = local.remoteId ?: continue
            val remote = remoteByUid[remoteId]?.let { toState(it, local, supportsDescription) }
            val state =
                LocalItemState(
                    name = local.name,
                    checked = local.isChecked,
                    quantity = local.quantity,
                    unit = local.unit,
                    hasPendingChanges = queue.hasPendingForItem(local.localId),
                    isDeleted = local.isDeleted,
                )
            when (ConflictResolver.resolve(state, remote, compareQuantity = supportsDescription)) {
                ItemResolution.IN_SYNC -> store.markItemSynced(local.localId)
                ItemResolution.KEEP_LOCAL -> Unit
                ItemResolution.APPLY_REMOTE ->
                    remote?.let { store.applyRemoteItem(local.localId, it.summary, it.quantity, it.unit, it.completed) }
                ItemResolution.DELETE_LOCAL ->
                    if (local.isDeleted) store.purgeItem(local.localId) else store.removeRemotelyDeletedItem(local.localId)
                ItemResolution.RECREATE_REMOTE -> store.requeueCreation(local.localId)
            }
        }
        val claimed = locals.mapNotNull { it.remoteId }.toSet()
        remoteItems.filter { it.uid !in claimed }.forEach { remote ->
            val quantity = if (supportsDescription) ItemDescriptionCodec.decode(remote.description) else ItemQuantity(1.0, null)
            store.insertRemoteItem(listLocalId, remote.uid, remote.summary, quantity.quantity, quantity.unit, remote.completed)
        }
    }

    /** Without description support the remote has no quantity: the local one is kept. */
    private fun toState(
        remote: HaTodoItem,
        local: SyncItemRef,
        supportsDescription: Boolean,
    ): RemoteItemState {
        val quantity = if (supportsDescription) ItemDescriptionCodec.decode(remote.description) else ItemQuantity(local.quantity, local.unit)
        return RemoteItemState(remote.summary, remote.completed, quantity.quantity, quantity.unit)
    }
}
