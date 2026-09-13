package org.opensources.courses.feature.homeassistant.data.sync

import org.opensources.courses.core.sync.SyncOperation
import org.opensources.courses.core.sync.SyncOperationType
import org.opensources.courses.core.sync.SyncQueue
import org.opensources.courses.feature.catalog.domain.TextNormalizer
import org.opensources.courses.feature.homeassistant.domain.HaCredentials
import org.opensources.courses.feature.homeassistant.domain.HaTodoItem
import org.opensources.courses.feature.homeassistant.domain.HaTodoList
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantException
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantGateway
import org.opensources.courses.feature.homeassistant.domain.ItemDescriptionCodec

/**
 * Sends the pending item operations of one list.
 *
 * Operations are grouped per item and the item's *current* local state is sent once: five taps on
 * the same checkbox while offline become a single `update_item`. An operation is removed from the
 * queue only after Home Assistant accepted the request; on a non-fatal error it stays queued with
 * its attempt count incremented, on a fatal error (server unreachable, token refused) the whole
 * synchronisation stops and everything stays queued.
 */
class HaItemPusher(
    private val gateway: HomeAssistantGateway,
    private val store: SyncLocalStore,
    private val queue: SyncQueue,
) {
    /** Returns the number of items whose operations could not be sent. */
    suspend fun push(
        credentials: HaCredentials,
        remoteList: HaTodoList,
        listLocalId: String,
        remoteItems: List<HaTodoItem>,
    ): Int {
        val operations = queue.pending().filter { it.listLocalId == listLocalId && it.type.isItemOperation }
        if (operations.isEmpty()) return 0
        val items = store.items(listLocalId).associateBy { it.localId }
        val remoteUids = remoteItems.map { it.uid }.toSet()
        val awaitingUid = mutableListOf<Pair<SyncItemRef, List<Long>>>()
        var failures = 0
        for ((itemId, itemOperations) in operations.groupBy { it.itemLocalId }) {
            val ids = itemOperations.map { it.id }
            val item = itemId?.let(items::get)
            try {
                when {
                    item == null -> removeVanishedItem(credentials, remoteList, itemOperations, remoteUids, ids)
                    item.isDeleted -> {
                        item.remoteId?.takeIf { it in remoteUids }?.let { gateway.removeItem(credentials, remoteList.entityId, it) }
                        store.purgeItem(item.localId)
                        queue.complete(ids)
                    }
                    item.remoteId == null || item.remoteId !in remoteUids -> {
                        // New item, or deleted remotely while modified locally: the local change wins.
                        if (item.remoteId != null) store.setItemRemoteId(item.localId, null)
                        gateway.addItem(credentials, remoteList.entityId, item.name, description(item, remoteList))
                        awaitingUid += item.copy(remoteId = null) to ids
                    }
                    else -> {
                        sendState(credentials, remoteList, item, item.remoteId)
                        queue.complete(ids)
                    }
                }
            } catch (exception: HomeAssistantException) {
                if (exception.isFatal) throw exception
                queue.fail(ids, exception.kind.name)
                failures++
            }
        }
        if (awaitingUid.isNotEmpty()) failures += resolveCreatedUids(credentials, remoteList, listLocalId, awaitingUid)
        return failures
    }

    private suspend fun removeVanishedItem(
        credentials: HaCredentials,
        remoteList: HaTodoList,
        operations: List<SyncOperation>,
        remoteUids: Set<String>,
        ids: List<Long>,
    ) {
        operations
            .lastOrNull { it.type == SyncOperationType.DELETE_ITEM }
            ?.remoteItemId
            ?.takeIf { it in remoteUids }
            ?.let { gateway.removeItem(credentials, remoteList.entityId, it) }
        queue.complete(ids)
    }

    /**
     * `add_item` does not return the new uid: read the list again and take the most recent
     * unclaimed item with the same name. Its checked state is sent afterwards, because an item is
     * always created unchecked.
     */
    private suspend fun resolveCreatedUids(
        credentials: HaCredentials,
        remoteList: HaTodoList,
        listLocalId: String,
        awaitingUid: List<Pair<SyncItemRef, List<Long>>>,
    ): Int {
        val remoteItems = gateway.getItems(credentials, remoteList.entityId).asReversed()
        val claimed = store.items(listLocalId).mapNotNull { it.remoteId }.toMutableSet()
        var failures = 0
        for ((item, ids) in awaitingUid) {
            val key = TextNormalizer.normalize(item.name)
            val match = remoteItems.firstOrNull { it.uid !in claimed && TextNormalizer.normalize(it.summary) == key }
            if (match == null) {
                queue.fail(ids, UID_NOT_FOUND)
                failures++
                continue
            }
            claimed += match.uid
            store.setItemRemoteId(item.localId, match.uid)
            if (item.isChecked) sendState(credentials, remoteList, item, match.uid)
            queue.complete(ids)
        }
        return failures
    }

    private suspend fun sendState(
        credentials: HaCredentials,
        remoteList: HaTodoList,
        item: SyncItemRef,
        uid: String,
    ) {
        gateway.updateItem(
            credentials = credentials,
            entityId = remoteList.entityId,
            uid = uid,
            summary = item.name,
            completed = item.isChecked,
            description = description(item, remoteList),
            sendDescription = remoteList.supportsDescription,
        )
    }

    private fun description(
        item: SyncItemRef,
        remoteList: HaTodoList,
    ): String? = if (remoteList.supportsDescription) ItemDescriptionCodec.encode(item.quantity, item.unit) else null

    private companion object {
        const val UID_NOT_FOUND = "UID_NOT_FOUND"
    }
}
