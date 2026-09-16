package org.opensources.courses.feature.homeassistant.data.sync

import org.opensources.courses.core.model.SyncStatus
import org.opensources.courses.core.sync.SyncQueue
import org.opensources.courses.feature.catalog.domain.CatalogRepository
import org.opensources.courses.feature.homeassistant.domain.ConflictResolver
import org.opensources.courses.feature.homeassistant.domain.HaTodoItem
import org.opensources.courses.feature.homeassistant.domain.ItemDescriptionCodec
import org.opensources.courses.feature.homeassistant.domain.ItemQuantity
import org.opensources.courses.feature.homeassistant.domain.ItemResolution

/** Applies the remote state of one list to Room, following [ConflictResolver]. */
class HaItemReconciler(
    private val store: SyncLocalStore,
    private val queue: SyncQueue,
    private val catalog: CatalogRepository,
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
        val unclaimed = UnclaimedRemoteItems(remoteItems, locals.mapNotNull { it.remoteId })
        locals.filter { it.remoteId == null && !it.isDeleted }.forEach { local ->
            unclaimed.claim(local.name)?.let { match -> store.setItemRemoteId(local.localId, match.uid) }
        }
    }

    /**
     * The whole list is applied in one transaction, so the screen is updated once. Pending items are
     * read once for the whole list; the store still checks, item by item, that nothing was queued
     * since.
     */
    suspend fun reconcile(
        listLocalId: String,
        remoteItems: List<HaTodoItem>,
        supportsDescription: Boolean,
    ) = store.inTransaction {
        val locals = store.items(listLocalId)
        val pendingItemIds = queue.pendingItemIds(listLocalId)
        val remoteByUid = remoteItems.associateBy { it.uid }
        for (local in locals) {
            val remoteId = local.remoteId ?: continue
            val remote = remoteByUid[remoteId]?.toRemoteState(local, supportsDescription)
            val state = local.toLocalState(hasPendingChanges = local.localId in pendingItemIds)
            when (ConflictResolver.resolve(state, remote, compareQuantity = supportsDescription)) {
                // Most items are already in sync: nothing to write for them.
                ItemResolution.IN_SYNC -> if (local.syncStatus != SyncStatus.SYNCED) store.markItemSynced(local.localId)
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
            // Items written in Home Assistant join the food catalog, so they are suggested like any product.
            val productId = remote.summary.takeIf { it.isNotBlank() }?.let { catalog.getOrCreateCustomProduct(it).id }
            store.insertRemoteItem(listLocalId, remote.uid, remote.summary, quantity.quantity, quantity.unit, remote.completed, productId)
        }
    }
}
