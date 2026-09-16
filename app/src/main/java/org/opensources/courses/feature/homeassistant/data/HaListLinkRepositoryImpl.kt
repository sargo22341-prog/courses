package org.opensources.courses.feature.homeassistant.data

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
        private val writer: HaLocalListWriter,
        private val queue: SyncQueue,
        private val transactions: TransactionRunner,
        private val clock: Clock,
    ) : HaListLinkRepository {
        override suspend fun linkToExisting(
            listId: String,
            entityId: String,
        ) {
            transactions.inTransaction {
                if (listDao.getById(listId) == null) return@inTransaction
                // One Home Assistant list feeds at most one local list.
                listDao.getAll().filter { it.remoteId == entityId && it.localId != listId }.forEach { other ->
                    when {
                        other.importedFromRemote && !writer.hasPendingItemChanges(other.localId) -> writer.remove(other)
                        else -> writer.unlink(other)
                    }
                }
                // Read after the loop: removing a list may have made this one the default.
                val list = listDao.getById(listId) ?: return@inTransaction
                list.remoteId?.takeIf { it != entityId }?.let { writer.ignore(it) }
                writer.stopIgnoring(entityId)
                val tracked = trackedDao.getByEntityId(entityId)
                val sameRemote = list.remoteId == entityId
                resetItems(listId)
                listDao.update(
                    list.copy(
                        remoteId = entityId,
                        remoteEntryId = tracked?.configEntryId,
                        createdByApp = tracked != null,
                        importedFromRemote = list.importedFromRemote && sameRemote,
                        remoteName = list.remoteName.takeIf { sameRemote },
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
                // The Home Assistant list it leaves must not come back as an imported copy.
                list.remoteId?.let { writer.ignore(it) }
                resetItems(listId)
                listDao.update(
                    list.copy(
                        remoteId = null,
                        remoteEntryId = null,
                        createdByApp = true,
                        importedFromRemote = false,
                        remoteName = null,
                        syncStatus = SyncStatus.PENDING,
                        updatedAt = clock.millis(),
                    ),
                )
                // Queued first, so item creations for this list always run after it.
                queue.enqueue(SyncOperationType.CREATE_LIST, listLocalId = listId)
                enqueueItemCreations(listId)
            }
        }

        override suspend fun unlink(listId: String) {
            transactions.inTransaction {
                val list = listDao.getById(listId) ?: return@inTransaction
                list.remoteId?.let { writer.ignore(it) }
                writer.unlink(list)
            }
        }

        override suspend fun removeImportedLists() {
            transactions.inTransaction { writer.removeImportedLists() }
        }

        override suspend fun unlinkAll() {
            transactions.inTransaction {
                listDao.getAll().filter { it.remoteId != null || it.syncStatus != SyncStatus.LOCAL_ONLY }.forEach { writer.unlink(it) }
                // Also the deletions of lists already gone from this phone.
                queue.clear()
            }
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
