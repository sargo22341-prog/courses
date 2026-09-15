package org.opensources.courses.feature.homeassistant.data

import org.opensources.courses.core.model.SyncStatus
import org.opensources.courses.core.sync.SyncQueue
import org.opensources.courses.feature.homeassistant.data.local.HaIgnoredListDao
import org.opensources.courses.feature.homeassistant.data.local.HaIgnoredListEntity
import org.opensources.courses.feature.lists.data.ShoppingListDao
import org.opensources.courses.feature.lists.data.ShoppingListEntity
import org.opensources.courses.feature.shopping.data.ShoppingItemDao
import java.time.Clock
import javax.inject.Inject

/**
 * Local writes on lists linked to Home Assistant, shared by the link repository and the
 * synchronisation store. Every method must run inside a transaction opened by its caller.
 */
class HaLocalListWriter
    @Inject
    constructor(
        private val listDao: ShoppingListDao,
        private val itemDao: ShoppingItemDao,
        private val ignoredDao: HaIgnoredListDao,
        private val queue: SyncQueue,
        private val clock: Clock,
    ) {
        /** Stops synchronising the list; it stays on this phone with its items. */
        suspend fun unlink(list: ShoppingListEntity) {
            queue.clearList(list.localId)
            itemDao.getAllForList(list.localId).forEach { item ->
                if (item.isDeleted) {
                    itemDao.delete(item.localId)
                } else {
                    itemDao.update(item.copy(remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY))
                }
            }
            listDao.update(
                list.copy(
                    remoteId = null,
                    remoteEntryId = null,
                    createdByApp = false,
                    importedFromRemote = false,
                    remoteName = null,
                    syncStatus = SyncStatus.LOCAL_ONLY,
                    updatedAt = clock.millis(),
                ),
            )
        }

        /**
         * Removes the list and its items from this phone; nothing is deleted remotely. The application
         * always keeps one list: the last one is unlinked instead.
         */
        suspend fun remove(list: ShoppingListEntity) {
            val remaining = listDao.getAll().filter { it.localId != list.localId }
            if (remaining.isEmpty()) {
                unlink(list)
                return
            }
            queue.clearList(list.localId)
            // Its items go with it (foreign key cascade).
            listDao.delete(list.localId)
            if (list.isDefault) listDao.setDefault(remaining.first().localId)
        }

        /** The "all lists" mode was left: the lists it imported leave this phone, the default one last. */
        suspend fun removeImportedLists() {
            listDao.getAll().filter { it.importedFromRemote }.sortedBy { it.isDefault }.forEach { imported ->
                // Read again: removing a default list moves the default flag.
                listDao.getById(imported.localId)?.let { remove(it) }
            }
        }

        suspend fun hasPendingItemChanges(listLocalId: String): Boolean =
            queue.pending().any { it.listLocalId == listLocalId && it.type.isItemOperation }

        /** The user parted with this Home Assistant list: the "all lists" mode must not bring it back. */
        suspend fun ignore(entityId: String) = ignoredDao.upsert(HaIgnoredListEntity(entityId, clock.millis()))

        suspend fun stopIgnoring(entityId: String) = ignoredDao.delete(entityId)

        suspend fun ignoredEntityIds(): Set<String> = ignoredDao.getEntityIds().toSet()
    }
