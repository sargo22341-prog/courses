package org.opensources.courses.feature.lists.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.opensources.courses.core.database.TransactionRunner
import org.opensources.courses.core.model.SyncStatus
import org.opensources.courses.core.sync.RemoteSyncEngine
import org.opensources.courses.core.sync.SyncOperationType
import org.opensources.courses.core.sync.SyncQueue
import org.opensources.courses.feature.lists.domain.ShoppingList
import org.opensources.courses.feature.lists.domain.ShoppingListRepository
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

class ShoppingListRepositoryImpl
    @Inject
    constructor(
        private val dao: ShoppingListDao,
        private val queue: SyncQueue,
        private val remoteSync: RemoteSyncEngine,
        private val transactions: TransactionRunner,
        private val clock: Clock,
    ) : ShoppingListRepository {
        override fun observeLists(): Flow<List<ShoppingList>> = dao.observeAll().map { lists -> lists.map { it.toDomain() } }

        override fun observeList(id: String): Flow<ShoppingList?> = dao.observeById(id).map { it?.toDomain() }

        override fun observeDefaultList(): Flow<ShoppingList?> = dao.observeDefault().map { it?.toDomain() }

        /**
         * When the remote asks for it (automatic list creation enabled), the list is synchronised from
         * the start: its creation is queued in the same transaction, so it cannot be forgotten.
         */
        override suspend fun createList(name: String): ShoppingList {
            val synchronize = remoteSync.synchronizesNewLists()
            return transactions.inTransaction { insertList(name, synchronize) }
        }

        private suspend fun insertList(
            name: String,
            synchronize: Boolean,
        ): ShoppingList {
            val now = clock.millis()
            val entity =
                ShoppingListEntity(
                    localId = UUID.randomUUID().toString(),
                    name = name,
                    isDefault = dao.getDefault() == null,
                    createdAt = now,
                    updatedAt = now,
                    createdByApp = synchronize,
                    syncStatus = if (synchronize) SyncStatus.PENDING else SyncStatus.LOCAL_ONLY,
                )
            dao.insert(entity)
            if (synchronize) queue.enqueue(SyncOperationType.CREATE_LIST, listLocalId = entity.localId)
            return entity.toDomain()
        }

        override suspend fun renameList(
            id: String,
            name: String,
        ) {
            transactions.inTransaction {
                val list = dao.getById(id) ?: return@inTransaction
                if (list.name == name) return@inTransaction
                val synchronized = list.syncStatus != SyncStatus.LOCAL_ONLY
                dao.update(
                    list.copy(
                        name = name,
                        updatedAt = clock.millis(),
                        syncStatus = if (synchronized) SyncStatus.PENDING else list.syncStatus,
                    ),
                )
                if (synchronized) {
                    queue.enqueue(SyncOperationType.UPDATE_LIST, listLocalId = id, remoteListId = list.remoteId)
                }
            }
        }

        /**
         * Deleting a list linked to a Home Assistant list the user created elsewhere only unlinks it:
         * the remote list is removed only when this application created it.
         */
        override suspend fun deleteList(id: String): Boolean =
            transactions.inTransaction {
                val lists = dao.getAll()
                val list = lists.firstOrNull { it.localId == id } ?: return@inTransaction true
                if (lists.size <= 1) return@inTransaction false
                queue.clearList(id)
                if (list.createdByApp && list.remoteEntryId != null) {
                    queue.enqueue(
                        SyncOperationType.DELETE_LIST,
                        listLocalId = id,
                        remoteListId = list.remoteId,
                        remoteEntryId = list.remoteEntryId,
                    )
                }
                dao.delete(id)
                if (list.isDefault) {
                    lists.first { it.localId != id }.let { dao.setDefault(it.localId) }
                }
                true
            }

        override suspend fun setDefaultList(id: String) = dao.setDefault(id)

        override suspend fun ensureDefaultList(name: String): ShoppingList {
            val synchronize = remoteSync.synchronizesNewLists()
            return transactions.inTransaction {
                dao.getDefault()?.toDomain()
                    ?: dao.getAll().firstOrNull()?.let { existing ->
                        dao.setDefault(existing.localId)
                        existing.copy(isDefault = true).toDomain()
                    }
                    ?: insertList(name, synchronize)
            }
        }
    }
