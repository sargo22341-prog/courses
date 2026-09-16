package org.opensources.courses.core.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent, ordered queue of local changes to push to the remote.
 *
 * Writers enqueue inside the same Room transaction as the change itself, so a change and its
 * operation are always persisted together — even with no network at all.
 */
@Singleton
class SyncQueue
    @Inject
    constructor(
        private val dao: SyncOperationDao,
        private val clock: Clock,
    ) {
        suspend fun enqueue(
            type: SyncOperationType,
            listLocalId: String,
            itemLocalId: String? = null,
            remoteListId: String? = null,
            remoteItemId: String? = null,
            remoteEntryId: String? = null,
        ) {
            dao.insert(
                SyncOperationEntity(
                    type = type,
                    listLocalId = listLocalId,
                    itemLocalId = itemLocalId,
                    remoteListId = remoteListId,
                    remoteItemId = remoteItemId,
                    remoteEntryId = remoteEntryId,
                    createdAt = clock.millis(),
                ),
            )
        }

        suspend fun pending(): List<SyncOperation> = dao.getAll().map { it.toDomain() }

        /** Removes operations confirmed by the remote. */
        suspend fun complete(ids: List<Long>) {
            if (ids.isNotEmpty()) dao.deleteByIds(ids)
        }

        /**
         * Keeps the operations for a later retry and counts the refusal. Only refusals are counted:
         * while the remote cannot be reached nothing is attempted.
         */
        suspend fun fail(ids: List<Long>) {
            if (ids.isNotEmpty()) dao.markFailed(ids)
        }

        suspend fun hasPendingForItem(itemLocalId: String): Boolean = dao.countForItem(itemLocalId) > 0

        /** Items of the list with an operation still waiting: remote data must not overwrite them. */
        suspend fun pendingItemIds(listLocalId: String): Set<String> = dao.getItemIdsForList(listLocalId).toSet()

        suspend fun clearList(listLocalId: String) = dao.deleteForList(listLocalId)

        /** The remote is forgotten: nothing left can be sent anywhere. */
        suspend fun clear() = dao.deleteAll()

        fun observePendingCount(): Flow<Int> = dao.observeCount().distinctUntilChanged()

        companion object {
            /**
             * Refusals after which an operation is given up rather than retried forever
             * (docs/adr/0022-abandon-des-operations-refusees.md).
             */
            const val MAX_ATTEMPTS = 10
        }
    }
