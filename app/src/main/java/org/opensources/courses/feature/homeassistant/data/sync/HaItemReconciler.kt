package org.opensources.courses.feature.homeassistant.data.sync

import org.opensources.courses.core.model.SyncStatus
import org.opensources.courses.core.sync.SyncQueue
import org.opensources.courses.feature.catalog.domain.CatalogRepository
import org.opensources.courses.feature.catalog.domain.CatalogSource
import org.opensources.courses.feature.catalog.domain.FindProductInTextUseCase
import org.opensources.courses.feature.catalog.domain.TextNormalizer
import org.opensources.courses.feature.homeassistant.domain.ConflictResolver
import org.opensources.courses.feature.homeassistant.domain.HaItemFormat
import org.opensources.courses.feature.homeassistant.domain.HaTodoItem
import org.opensources.courses.feature.homeassistant.domain.ItemResolution
import org.opensources.courses.feature.homeassistant.domain.RemoteItemState

/**
 * Applies the remote state of one list to Room, following [ConflictResolver]. Items are read in the
 * [HaItemFormat] of their list.
 *
 * Items written in Home Assistant join the food catalog, so they are suggested like any product.
 * Mealie items wrap the food in recipe words ("gousse ail"): the bundled or OpenFoodFacts product
 * named in their text is looked for first ([FindProductInTextUseCase]), also for items already in
 * sync that are still filed under a custom product (read before Mealie was supported). A custom
 * product that only held the former text of an item is removed once nothing uses it.
 */
class HaItemReconciler(
    private val store: SyncLocalStore,
    private val queue: SyncQueue,
    private val catalog: CatalogRepository,
    private val findProductInText: FindProductInTextUseCase,
) {
    /**
     * Local items without a remote id take the uid of an unclaimed remote item with the same
     * name. Used when a list is linked to a non-empty Home Assistant list.
     */
    suspend fun adoptByName(
        listLocalId: String,
        remoteItems: List<HaTodoItem>,
        format: HaItemFormat,
    ) {
        val locals = store.items(listLocalId)
        val unclaimed = UnclaimedRemoteItems(remoteItems, locals.mapNotNull { it.remoteId }, format)
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
        format: HaItemFormat,
    ) = store.inTransaction {
        val locals = store.items(listLocalId)
        val pendingItemIds = queue.pendingItemIds(listLocalId)
        val remoteByUid = remoteItems.associateBy { it.uid }
        val inCatalog = if (format == HaItemFormat.MEALIE) catalogProductIds(locals) else emptySet()
        for (local in locals) {
            val remoteId = local.remoteId ?: continue
            val remote = remoteByUid[remoteId]?.toRemoteState(local, format)
            val state = local.toLocalState(hasPendingChanges = local.localId in pendingItemIds)
            when (ConflictResolver.resolve(state, remote, compareQuantity = format.syncsQuantity)) {
                // Most items are already in sync: nothing to write for them.
                ItemResolution.IN_SYNC -> {
                    if (local.syncStatus != SyncStatus.SYNCED) store.markItemSynced(local.localId)
                    if (format == HaItemFormat.MEALIE && local.catalogProductId !in inCatalog) relink(local)
                }
                ItemResolution.KEEP_LOCAL -> Unit
                ItemResolution.APPLY_REMOTE -> remote?.let { applyRemote(local, it, format) }
                ItemResolution.DELETE_LOCAL ->
                    if (local.isDeleted) store.purgeItem(local.localId) else store.removeRemotelyDeletedItem(local.localId)
                ItemResolution.RECREATE_REMOTE -> store.requeueCreation(local.localId)
            }
        }
        val claimed = locals.mapNotNull { it.remoteId }.toSet()
        remoteItems.filter { it.uid !in claimed }.forEach { remote ->
            val content = format.read(remote)
            val quantity = content.quantity
            val productId = content.name.takeIf { it.isNotBlank() }?.let { productFor(it, format) }
            if (quantity == null) {
                store.insertRemoteItem(listLocalId, remote.uid, content.name, 1.0, null, remote.completed, productId)
            } else {
                store.insertRemoteItem(listLocalId, remote.uid, content.name, quantity, content.unit, remote.completed, productId)
            }
        }
    }

    /** Other lists keep the link rules of any rename; a renamed Mealie item looks for its product again. */
    private suspend fun applyRemote(
        local: SyncItemRef,
        remote: RemoteItemState,
        format: HaItemFormat,
    ) {
        val relinks = format == HaItemFormat.MEALIE && TextNormalizer.normalize(remote.name) != TextNormalizer.normalize(local.name)
        val productId = if (relinks && remote.name.isNotBlank()) productFor(remote.name, format) else null
        store.applyRemoteItem(local.localId, remote.name, remote.quantity, remote.unit, remote.completed, productId)
        // Typically the raw Mealie text ("250 grammes Pâtes") kept as a product before it was read.
        if (relinks) local.catalogProductId?.takeIf { it != productId }?.let { catalog.deleteUnusedCustomProduct(it) }
    }

    /** Ids of the bundled and OpenFoodFacts products the items are linked to. */
    private suspend fun catalogProductIds(items: List<SyncItemRef>): Set<String> =
        catalog
            .findByIds(items.mapNotNull { it.catalogProductId }.toSet())
            .filter { it.source != CatalogSource.CUSTOM }
            .map { it.id }
            .toSet()

    /** A Mealie item filed under a custom product, or none, takes the catalog product its text names, if any. */
    private suspend fun relink(item: SyncItemRef) {
        val productId = findProductInText(item.name) ?: return
        if (store.linkItemToProduct(item.localId, item.name, productId)) item.catalogProductId?.let { catalog.deleteUnusedCustomProduct(it) }
    }

    private suspend fun productFor(
        name: String,
        format: HaItemFormat,
    ): String = (if (format == HaItemFormat.MEALIE) findProductInText(name) else null) ?: catalog.getOrCreateCustomProduct(name).id
}
