package org.opensources.courses.feature.homeassistant.data.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.opensources.courses.core.database.TransactionRunner
import org.opensources.courses.core.model.SyncStatus
import org.opensources.courses.core.sync.SyncOperationType
import org.opensources.courses.core.sync.SyncQueue
import org.opensources.courses.feature.homeassistant.data.HaLocalListWriter
import org.opensources.courses.feature.homeassistant.data.local.HaTrackedListDao
import org.opensources.courses.feature.homeassistant.data.local.HaTrackedListEntity
import org.opensources.courses.feature.homeassistant.domain.HaListNameAllocator
import org.opensources.courses.feature.lists.data.ShoppingListDao
import org.opensources.courses.feature.lists.data.ShoppingListEntity
import org.opensources.courses.feature.shopping.data.ShoppingItemDao
import org.opensources.courses.feature.shopping.data.ShoppingItemEntity
import org.opensources.courses.feature.shopping.data.renamed
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomSyncLocalStore
    @Inject
    constructor(
        private val listDao: ShoppingListDao,
        private val itemDao: ShoppingItemDao,
        private val trackedDao: HaTrackedListDao,
        private val writer: HaLocalListWriter,
        private val queue: SyncQueue,
        private val transactions: TransactionRunner,
        private val clock: Clock,
    ) : SyncLocalStore {
        override suspend fun synchronizedLists(): List<SyncListRef> =
            listDao.getSynchronized().map { SyncListRef(it.localId, it.name, it.remoteId, it.importedFromRemote, it.remoteName) }

        override fun observeLinkedEntityIds(): Flow<Set<String>> =
            listDao.observeAll().map { lists -> lists.mapNotNull { it.remoteId }.toSet() }.distinctUntilChanged()

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

        override suspend fun ignoredEntityIds(): Set<String> = writer.ignoredEntityIds()

        override suspend fun ignoreList(entityId: String) = writer.ignore(entityId)

        override suspend fun importList(
            entityId: String,
            remoteName: String,
        ) {
            transactions.inTransaction {
                val lists = listDao.getAll()
                if (lists.any { it.remoteId == entityId } || entityId in writer.ignoredEntityIds()) return@inTransaction
                val tracked = trackedDao.getByEntityId(entityId)
                val now = clock.millis()
                listDao.insert(
                    ShoppingListEntity(
                        localId = UUID.randomUUID().toString(),
                        name = HaListNameAllocator.uniqueName(remoteName, lists.map { it.name }),
                        isDefault = lists.none { it.isDefault },
                        createdAt = now,
                        updatedAt = now,
                        remoteId = entityId,
                        remoteEntryId = tracked?.configEntryId,
                        createdByApp = tracked != null,
                        importedFromRemote = true,
                        remoteName = remoteName,
                        syncStatus = SyncStatus.SYNCED,
                    ),
                )
            }
        }

        override suspend fun applyRemoteListName(
            listLocalId: String,
            remoteName: String,
        ) {
            transactions.inTransaction {
                val list = listDao.getById(listLocalId) ?: return@inTransaction
                if (!list.importedFromRemote || list.remoteName == remoteName) return@inTransaction
                val otherNames = listDao.getAll().filter { it.localId != listLocalId }.map { it.name }
                listDao.update(
                    list.copy(name = HaListNameAllocator.uniqueName(remoteName, otherNames), remoteName = remoteName, updatedAt = clock.millis()),
                )
            }
        }

        override suspend fun unlinkList(listLocalId: String) {
            transactions.inTransaction { listDao.getById(listLocalId)?.let { writer.unlink(it) } }
        }

        override suspend fun removeRemotelyDeletedList(listLocalId: String) {
            transactions.inTransaction {
                val list = listDao.getById(listLocalId) ?: return@inTransaction
                // Changes not sent yet are never lost: such a list stays, unlinked.
                if (writer.hasPendingItemChanges(listLocalId)) {
                    writer.unlink(list)
                } else {
                    writer.remove(list)
                }
            }
        }

        override suspend fun removeImportedLists() {
            transactions.inTransaction { writer.removeImportedLists() }
        }

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

        override suspend fun restoreDeletedItem(itemLocalId: String) {
            transactions.inTransaction {
                val item = itemDao.getById(itemLocalId) ?: return@inTransaction
                itemDao.update(item.copy(isDeleted = false, syncStatus = SyncStatus.SYNCED, updatedAt = clock.millis()))
            }
        }

        override suspend fun abandonItemChanges(
            itemLocalId: String,
            operationIds: List<Long>,
        ) {
            transactions.inTransaction {
                queue.complete(operationIds)
                val item = itemDao.getById(itemLocalId) ?: return@inTransaction
                val status =
                    when {
                        // Changed again while the synchronisation was running: that change is still sent.
                        queue.hasPendingForItem(itemLocalId) -> SyncStatus.PENDING
                        item.remoteId != null -> SyncStatus.SYNCED
                        else -> SyncStatus.LOCAL_ONLY
                    }
                itemDao.update(item.copy(isDeleted = false, syncStatus = status, updatedAt = clock.millis()))
            }
        }

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
                item.renamed(name).copy(
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
            catalogProductId: String?,
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
                    catalogProductId = catalogProductId,
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
