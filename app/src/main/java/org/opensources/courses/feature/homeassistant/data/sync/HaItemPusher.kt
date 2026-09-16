package org.opensources.courses.feature.homeassistant.data.sync

import org.opensources.courses.core.sync.SyncOperation
import org.opensources.courses.core.sync.SyncOperationType
import org.opensources.courses.core.sync.SyncQueue
import org.opensources.courses.feature.homeassistant.domain.ConflictResolver
import org.opensources.courses.feature.homeassistant.domain.HaCredentials
import org.opensources.courses.feature.homeassistant.domain.HaTodoItem
import org.opensources.courses.feature.homeassistant.domain.HaTodoList
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantException
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantGateway
import org.opensources.courses.feature.homeassistant.domain.ItemDescriptionCodec
import org.opensources.courses.feature.language.domain.AppLanguageRepository

/**
 * Sends the pending item operations of one list.
 *
 * Operations are grouped per item and the item's *current* local state is sent once: five taps on
 * the same checkbox while offline become a single `update_item`. Only the fields changed locally
 * are sent, so a change made meanwhile in Home Assistant to the other fields is kept. An operation
 * is removed from the queue only after Home Assistant accepted the request. A refusal only affects
 * its item: the operations stay queued with their attempt count incremented, and are given up once
 * Home Assistant refused them [SyncQueue.MAX_ATTEMPTS] times. On a fatal error (server unreachable,
 * token refused) the whole synchronisation stops and everything stays queued.
 */
class HaItemPusher(
    private val gateway: HomeAssistantGateway,
    private val store: SyncLocalStore,
    private val queue: SyncQueue,
    private val languages: AppLanguageRepository,
) {
    /** [operations] are the pending item operations of the list, read at the start of the synchronisation. */
    suspend fun push(
        credentials: HaCredentials,
        remoteList: HaTodoList,
        listLocalId: String,
        operations: List<SyncOperation>,
        remoteItems: List<HaTodoItem>,
        tally: SyncTally,
    ) {
        if (operations.isEmpty()) return
        val items = store.items(listLocalId).associateBy { it.localId }
        val remoteByUid = remoteItems.associateBy { it.uid }
        val awaitingUid = mutableListOf<Pair<SyncItemRef, List<SyncOperation>>>()
        for ((itemId, itemOperations) in operations.groupBy { it.itemLocalId }) {
            val item = itemId?.let(items::get)
            val remote = item?.remoteId?.let(remoteByUid::get)
            refusable(item, itemOperations, tally) {
                when {
                    item == null -> removeVanishedItem(credentials, remoteList, itemOperations, remoteByUid.keys)
                    item.isDeleted -> pushDeletion(credentials, remoteList, item, remote, itemOperations)
                    remote == null -> {
                        // New item, or deleted remotely while modified locally: the local change wins.
                        if (item.remoteId != null) store.setItemRemoteId(item.localId, null)
                        gateway.addItem(credentials, remoteList.entityId, item.name, description(item, remoteList))
                        awaitingUid += item.copy(remoteId = null) to itemOperations
                    }
                    else -> {
                        sendChanges(credentials, remoteList, item, remote, itemOperations)
                        queue.complete(itemOperations.map { it.id })
                    }
                }
            }
        }
        if (awaitingUid.isNotEmpty()) resolveCreatedUids(credentials, remoteList, listLocalId, awaitingUid, tally)
    }

    /**
     * Runs [send] for one item. A refusal is kept for a later retry, unless it was the last attempt:
     * the changes are then given up and the item takes the Home Assistant state again.
     */
    private suspend fun refusable(
        item: SyncItemRef?,
        operations: List<SyncOperation>,
        tally: SyncTally,
        send: suspend () -> Unit,
    ) {
        try {
            send()
        } catch (exception: HomeAssistantException) {
            if (exception.isFatal) throw exception
            val ids = operations.map { it.id }
            when {
                operations.none { it.isLastAttempt } -> {
                    queue.fail(ids)
                    tally.retried++
                }
                item == null -> {
                    queue.complete(ids)
                    tally.abandoned++
                }
                else -> {
                    store.abandonItemChanges(item.localId, ids)
                    tally.abandoned++
                }
            }
        }
    }

    private suspend fun removeVanishedItem(
        credentials: HaCredentials,
        remoteList: HaTodoList,
        operations: List<SyncOperation>,
        remoteUids: Set<String>,
    ) {
        operations
            .lastOrNull { it.type == SyncOperationType.DELETE_ITEM }
            ?.remoteItemId
            ?.takeIf { it in remoteUids }
            ?.let { gateway.removeItem(credentials, remoteList.entityId, it) }
        queue.complete(operations.map { it.id })
    }

    /** The deletion is sent, unless the item was modified in Home Assistant since the last sync. */
    private suspend fun pushDeletion(
        credentials: HaCredentials,
        remoteList: HaTodoList,
        item: SyncItemRef,
        remote: HaTodoItem?,
        operations: List<SyncOperation>,
    ) {
        val ids = operations.map { it.id }
        if (remote != null) {
            val onlyDeleted = operations.all { it.type == SyncOperationType.DELETE_ITEM }
            val remoteState = remote.toRemoteState(item, remoteList.supportsDescription)
            if (onlyDeleted && ConflictResolver.remoteChangedSince(item.toLocalState(false), remoteState, remoteList.supportsDescription)) {
                queue.complete(ids)
                store.restoreDeletedItem(item.localId)
                return
            }
            gateway.removeItem(credentials, remoteList.entityId, remote.uid)
        }
        store.purgeItem(item.localId)
        queue.complete(ids)
    }

    /**
     * A creation sends the whole item. Otherwise the name and quantity are sent only if they were
     * edited, and the checked state only if it was toggled and Home Assistant did not complete the
     * item after that ([ConflictResolver.localStatusWins]). Fields not sent take the remote value at
     * the reconciliation that follows.
     */
    private suspend fun sendChanges(
        credentials: HaCredentials,
        remoteList: HaTodoList,
        item: SyncItemRef,
        remote: HaTodoItem,
        operations: List<SyncOperation>,
    ) {
        val created = operations.any { it.type == SyncOperationType.CREATE_ITEM }
        val contentChanged = created || operations.any { it.type == SyncOperationType.UPDATE_ITEM }
        val statusChange = operations.lastOrNull { it.type == SyncOperationType.CHECK_ITEM || it.type == SyncOperationType.UNCHECK_ITEM }
        val remoteState = remote.toRemoteState(item, remoteList.supportsDescription)
        val sendStatus = created || (statusChange != null && ConflictResolver.localStatusWins(statusChange.createdAt, remoteState))
        if (!contentChanged && !sendStatus) return
        gateway.updateItem(
            credentials = credentials,
            entityId = remoteList.entityId,
            uid = remote.uid,
            summary = item.name.takeIf { contentChanged },
            completed = item.isChecked.takeIf { sendStatus },
            description = description(item, remoteList),
            sendDescription = contentChanged && remoteList.supportsDescription,
        )
    }

    /**
     * `add_item` does not return the new uid: read the list again and take the most recent
     * unclaimed item with the same name. Its checked state is sent afterwards, because an item is
     * always created unchecked.
     *
     * Without a match, Home Assistant stored the item under another text. Sending it again would
     * only add another copy: the local item leaves, and the reconciliation that follows imports the
     * Home Assistant copy instead.
     */
    private suspend fun resolveCreatedUids(
        credentials: HaCredentials,
        remoteList: HaTodoList,
        listLocalId: String,
        awaitingUid: List<Pair<SyncItemRef, List<SyncOperation>>>,
        tally: SyncTally,
    ) {
        val unclaimed = UnclaimedRemoteItems(gateway.getItems(credentials, remoteList.entityId).asReversed(), store.items(listLocalId).mapNotNull { it.remoteId })
        for ((item, operations) in awaitingUid) {
            val ids = operations.map { it.id }
            val match = unclaimed.claim(item.name)
            if (match == null) {
                store.purgeItem(item.localId)
                queue.complete(ids)
                continue
            }
            // Linked before its state is sent: whatever happens next, it is never created twice.
            store.setItemRemoteId(item.localId, match.uid)
            refusable(item, operations, tally) {
                if (item.isChecked) {
                    gateway.updateItem(credentials, remoteList.entityId, match.uid, null, completed = true, description = null, sendDescription = false)
                }
                queue.complete(ids)
            }
        }
    }

    private fun description(
        item: SyncItemRef,
        remoteList: HaTodoList,
    ): String? = if (remoteList.supportsDescription) ItemDescriptionCodec.encode(item.quantity, item.unit, languages.language.value.locale) else null
}
