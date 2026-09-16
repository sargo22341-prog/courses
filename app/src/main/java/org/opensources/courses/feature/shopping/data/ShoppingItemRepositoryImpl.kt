package org.opensources.courses.feature.shopping.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.opensources.courses.core.database.TransactionRunner
import org.opensources.courses.core.model.SyncStatus
import org.opensources.courses.core.sync.SyncOperationType
import org.opensources.courses.core.sync.SyncQueue
import org.opensources.courses.feature.lists.data.ShoppingListDao
import org.opensources.courses.feature.shopping.domain.NewShoppingItem
import org.opensources.courses.feature.shopping.domain.ShoppingItem
import org.opensources.courses.feature.shopping.domain.ShoppingItemRepository
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

/**
 * Every mutation is written to Room first. When the item's list is synchronised with Home
 * Assistant, the matching [SyncOperationType] is enqueued in the same transaction and the item is
 * flagged [SyncStatus.PENDING] so remote data cannot overwrite it before it has been pushed.
 */
class ShoppingItemRepositoryImpl
    @Inject
    constructor(
        private val itemDao: ShoppingItemDao,
        private val listDao: ShoppingListDao,
        private val queue: SyncQueue,
        private val transactions: TransactionRunner,
        private val clock: Clock,
    ) : ShoppingItemRepository {
        override fun observeItems(listId: String): Flow<List<ShoppingItem>> =
            itemDao.observeActiveForList(listId).map { items -> items.map { it.toDomain() } }

        override suspend fun getItems(listId: String): List<ShoppingItem> = itemDao.getActiveForList(listId).map { it.toDomain() }

        override suspend fun getAllItems(): List<ShoppingItem> = itemDao.getAllActive().map { it.toDomain() }

        override suspend fun addItem(item: NewShoppingItem): ShoppingItem =
            transactions.inTransaction {
                val synchronized = isSynchronized(item.listId)
                val now = clock.millis()
                val entity =
                    ShoppingItemEntity(
                        localId = UUID.randomUUID().toString(),
                        listLocalId = item.listId,
                        name = item.name,
                        quantity = item.quantity,
                        unit = item.unit,
                        isChecked = false,
                        catalogProductId = item.catalogProductId,
                        createdAt = now,
                        updatedAt = now,
                        syncStatus = if (synchronized) SyncStatus.PENDING else SyncStatus.LOCAL_ONLY,
                    )
                itemDao.insert(entity)
                if (synchronized) queue.enqueue(SyncOperationType.CREATE_ITEM, item.listId, entity.localId)
                entity.toDomain()
            }

        override suspend fun updateItem(
            itemId: String,
            name: String,
            quantity: Double,
            unit: String?,
        ) = mutate(itemId, SyncOperationType.UPDATE_ITEM) { it.renamed(name).copy(quantity = quantity, unit = unit) }

        // Home Assistant has no such field: nothing is queued and the item does not count as modified.
        override suspend fun setCatalogProduct(
            itemId: String,
            expectedName: String,
            catalogProductId: String,
        ): Boolean = itemDao.updateCatalogProduct(itemId, expectedName, catalogProductId) > 0

        override suspend fun setChecked(
            itemId: String,
            checked: Boolean,
        ) = mutate(itemId, if (checked) SyncOperationType.CHECK_ITEM else SyncOperationType.UNCHECK_ITEM) {
            it.copy(isChecked = checked)
        }

        override suspend fun deleteItem(itemId: String) {
            transactions.inTransaction { deleteInTransaction(itemId) }
        }

        override suspend fun deletePurchased(listId: String): Int =
            transactions.inTransaction {
                // A list kept on this phone only has nothing to send: one statement is enough.
                if (!isSynchronized(listId)) return@inTransaction itemDao.deleteChecked(listId)
                val purchased = itemDao.getActiveForList(listId).filter { it.isChecked }
                purchased.forEach { delete(it, synchronized = true) }
                purchased.size
            }

        private suspend fun deleteInTransaction(itemId: String) {
            val item = itemDao.getById(itemId) ?: return
            delete(item, isSynchronized(item.listLocalId))
        }

        private suspend fun delete(
            item: ShoppingItemEntity,
            synchronized: Boolean,
        ) {
            if (synchronized && item.remoteId != null) {
                itemDao.update(item.copy(isDeleted = true, updatedAt = clock.millis(), syncStatus = SyncStatus.PENDING))
                queue.enqueue(
                    SyncOperationType.DELETE_ITEM,
                    listLocalId = item.listLocalId,
                    itemLocalId = item.localId,
                    remoteItemId = item.remoteId,
                )
            } else {
                // Never reached Home Assistant: a pending CREATE_ITEM is dropped by the engine
                // because the item no longer exists.
                itemDao.delete(item.localId)
            }
        }

        private suspend fun mutate(
            itemId: String,
            operation: SyncOperationType,
            change: (ShoppingItemEntity) -> ShoppingItemEntity,
        ) {
            transactions.inTransaction {
                val current = itemDao.getById(itemId) ?: return@inTransaction
                val changed = change(current)
                if (changed == current) return@inTransaction
                val synchronized = isSynchronized(current.listLocalId)
                itemDao.update(
                    changed.copy(
                        updatedAt = clock.millis(),
                        syncStatus = if (synchronized) SyncStatus.PENDING else SyncStatus.LOCAL_ONLY,
                    ),
                )
                if (synchronized) queue.enqueue(operation, current.listLocalId, itemId, remoteItemId = current.remoteId)
            }
        }

        private suspend fun isSynchronized(listId: String): Boolean =
            listDao.getById(listId)?.let { it.syncStatus != SyncStatus.LOCAL_ONLY } == true
    }
