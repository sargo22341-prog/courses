package org.opensources.courses.feature.homeassistant.data.sync

import org.opensources.courses.core.database.TransactionRunner
import org.opensources.courses.core.model.SyncStatus
import org.opensources.courses.core.sync.SyncOperationType
import org.opensources.courses.core.sync.SyncQueue
import org.opensources.courses.feature.homeassistant.data.local.HaTrackedListDao
import org.opensources.courses.feature.homeassistant.data.local.HaTrackedListEntity
import org.opensources.courses.feature.homeassistant.domain.HaListLinkRepository
import org.opensources.courses.feature.lists.data.ShoppingListDao
import org.opensources.courses.feature.shopping.data.ShoppingItemDao
import org.opensources.courses.feature.shopping.data.ShoppingItemEntity
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

class RoomSyncLocalStore
    @Inject
    constructor(
        private val listDao: ShoppingListDao,
        private val itemDao: ShoppingItemDao,
        private val trackedDao: HaTrackedListDao,
        private val linkRepository: HaListLinkRepository,
        private val queue: SyncQueue,
        private val transactions: TransactionRunner,
        private val clock: Clock,
    ) : SyncLocalStore {
        override suspend fun synchronizedLists(): List<SyncListRef> = listDao.getSynchronized().map { SyncListRef(it.localId, it.name, it.remoteId) }

        override suspend fun items(listLocalId: String): List<SyncItemRef> =
            itemDao.getAllForList(listLocalId).map {
                SyncItemRef(it.localId, it.listLocalId, it.name, it.quantity, it.unit, it.isChecked, it.remoteId, it.isDeleted)
            }

        override suspend fun setListRemote(
            listLocalId: String,
            entityId: String,
            configEntryId: String?,
            name: String,
        ) {
            transactions.inTransaction {
                val list = listDao.getById(listLocalId) ?: return@inTransaction
                listDao.update(list.copy(remoteId = entityId, remoteEntryId = configEntryId, createdByApp = true, syncStatus = SyncStatus.SYNCED))
                trackedDao.upsert(HaTrackedListEntity(entityId, configEntryId, name, clock.millis()))
            }
        }

        override suspend fun markListSynced(listLocalId: String) {
            val list = listDao.getById(listLocalId) ?: return
            if (list.remoteId != null && list.syncStatus == SyncStatus.PENDING) listDao.update(list.copy(syncStatus = SyncStatus.SYNCED))
        }

        override suspend fun unlinkList(listLocalId: String) = linkRepository.unlink(listLocalId)

        override suspend fun forgetTrackedList(entityId: String) = trackedDao.delete(entityId)

        override suspend fun setItemRemoteId(
            itemLocalId: String,
            remoteId: String?,
        ) {
            transactions.inTransaction {
                val item = itemDao.getById(itemLocalId) ?: return@inTransaction
                itemDao.update(item.copy(remoteId = remoteId))
            }
        }

        override suspend fun purgeItem(itemLocalId: String) = itemDao.delete(itemLocalId)

        override suspend fun markItemSynced(itemLocalId: String) =
            withoutPendingChanges(itemLocalId) { item ->
                if (item.syncStatus != SyncStatus.SYNCED) itemDao.update(item.copy(syncStatus = SyncStatus.SYNCED))
            }

        override suspend fun removeRemotelyDeletedItem(itemLocalId: String) = withoutPendingChanges(itemLocalId) { itemDao.delete(it.localId) }

        override suspend fun applyRemoteItem(
            itemLocalId: String,
            name: String,
            quantity: Double,
            unit: String?,
            checked: Boolean,
        ) = withoutPendingChanges(itemLocalId) { item ->
            itemDao.update(
                item.copy(
                    name = name,
                    quantity = quantity,
                    unit = unit,
                    isChecked = checked,
                    updatedAt = clock.millis(),
                    syncStatus = SyncStatus.SYNCED,
                ),
            )
        }

        override suspend fun insertRemoteItem(
            listLocalId: String,
            remoteId: String,
            name: String,
            quantity: Double,
            unit: String?,
            checked: Boolean,
        ) {
            val now = clock.millis()
            itemDao.insert(
                ShoppingItemEntity(
                    localId = UUID.randomUUID().toString(),
                    listLocalId = listLocalId,
                    name = name,
                    quantity = quantity,
                    unit = unit,
                    isChecked = checked,
                    catalogProductId = null,
                    createdAt = now,
                    updatedAt = now,
                    remoteId = remoteId,
                    syncStatus = SyncStatus.SYNCED,
                ),
            )
        }

        override suspend fun requeueCreation(itemLocalId: String) {
            transactions.inTransaction {
                val item = itemDao.getById(itemLocalId) ?: return@inTransaction
                itemDao.update(item.copy(remoteId = null, syncStatus = SyncStatus.PENDING))
                queue.enqueue(SyncOperationType.CREATE_ITEM, item.listLocalId, item.localId)
            }
        }

        private suspend fun withoutPendingChanges(
            itemLocalId: String,
            block: suspend (ShoppingItemEntity) -> Unit,
        ) {
            transactions.inTransaction {
                val item = itemDao.getById(itemLocalId) ?: return@inTransaction
                if (!item.isDeleted && !queue.hasPendingForItem(itemLocalId)) block(item)
            }
        }
    }
