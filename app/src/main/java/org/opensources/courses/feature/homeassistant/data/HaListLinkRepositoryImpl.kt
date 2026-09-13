package org.opensources.courses.feature.homeassistant.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.opensources.courses.core.database.TransactionRunner
import org.opensources.courses.core.model.SyncStatus
import org.opensources.courses.core.sync.SyncOperationType
import org.opensources.courses.core.sync.SyncQueue
import org.opensources.courses.feature.homeassistant.data.local.HaTrackedListDao
import org.opensources.courses.feature.homeassistant.domain.HaListLinkRepository
import org.opensources.courses.feature.lists.data.ShoppingListDao
import org.opensources.courses.feature.shopping.data.ShoppingItemDao
import java.time.Clock
import javax.inject.Inject

class HaListLinkRepositoryImpl
    @Inject
    constructor(
        private val listDao: ShoppingListDao,
        private val itemDao: ShoppingItemDao,
        private val trackedDao: HaTrackedListDao,
        private val queue: SyncQueue,
        private val transactions: TransactionRunner,
        private val clock: Clock,
    ) : HaListLinkRepository {
        override fun observeTrackedEntityIds(): Flow<Set<String>> = trackedDao.observeAll().map { lists -> lists.map { it.entityId }.toSet() }

        override suspend fun linkToExisting(
            listId: String,
            entityId: String,
        ) {
            transactions.inTransaction {
                val list = listDao.getById(listId) ?: return@inTransaction
                // One Home Assistant list feeds at most one local list.
                listDao.getAll().filter { it.remoteId == entityId && it.localId != listId }.forEach { unlinkInTransaction(it.localId) }
                val tracked = trackedDao.getByEntityId(entityId)
                resetItems(listId)
                listDao.update(
                    list.copy(
                        remoteId = entityId,
                        remoteEntryId = tracked?.configEntryId,
                        createdByApp = tracked != null,
                        syncStatus = SyncStatus.SYNCED,
                        updatedAt = clock.millis(),
                    ),
                )
                enqueueItemCreations(listId)
            }
        }

        override suspend fun createInHomeAssistant(listId: String) {
            transactions.inTransaction {
                val list = listDao.getById(listId) ?: return@inTransaction
                resetItems(listId)
                listDao.update(
                    list.copy(remoteId = null, remoteEntryId = null, createdByApp = true, syncStatus = SyncStatus.PENDING, updatedAt = clock.millis()),
                )
                // Queued first, so item creations for this list always run after it.
                queue.enqueue(SyncOperationType.CREATE_LIST, listLocalId = listId)
                enqueueItemCreations(listId)
            }
        }

        override suspend fun unlink(listId: String) {
            transactions.inTransaction { unlinkInTransaction(listId) }
        }

        private suspend fun unlinkInTransaction(listId: String) {
            val list = listDao.getById(listId) ?: return
            queue.clearList(listId)
            itemDao.getAllForList(listId).forEach { item ->
                if (item.isDeleted) {
                    itemDao.delete(item.localId)
                } else {
                    itemDao.update(item.copy(remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY))
                }
            }
            listDao.update(
                list.copy(remoteId = null, remoteEntryId = null, createdByApp = false, syncStatus = SyncStatus.LOCAL_ONLY, updatedAt = clock.millis()),
            )
        }

        /** Forgets previous remote ids and pending operations; tombstones are purged. */
        private suspend fun resetItems(listId: String) {
            queue.clearList(listId)
            itemDao.getAllForList(listId).forEach { item ->
                if (item.isDeleted) {
                    itemDao.delete(item.localId)
                } else {
                    itemDao.update(item.copy(remoteId = null, syncStatus = SyncStatus.PENDING))
                }
            }
        }

        /**
         * Every item is queued for creation. The engine first matches items by name with what
         * already exists remotely, so linking to a non-empty list never duplicates items.
         */
        private suspend fun enqueueItemCreations(listId: String) {
            itemDao.getActiveForList(listId).forEach { queue.enqueue(SyncOperationType.CREATE_ITEM, listId, it.localId) }
        }
    }
