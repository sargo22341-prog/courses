package org.opensources.courses.feature.homeassistant.data

import org.opensources.courses.core.model.SyncStatus
import org.opensources.courses.core.sync.SyncQueue
import org.opensources.courses.feature.homeassistant.data.local.HaIgnoredListDao
import org.opensources.courses.feature.homeassistant.data.local.HaIgnoredListEntity
import org.opensources.courses.feature.homeassistant.data.local.HaTrackedListDao
import org.opensources.courses.feature.homeassistant.domain.HaListNameAllocator
import org.opensources.courses.feature.lists.data.ShoppingListDao
import org.opensources.courses.feature.lists.data.ShoppingListEntity
import org.opensources.courses.feature.shopping.data.ShoppingItemDao
import java.time.Clock
import java.util.UUID
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
        private val trackedDao: HaTrackedListDao,
        private val queue: SyncQueue,
        private val clock: Clock,
    ) {
        /**
         * Adds a list linked to the Home Assistant list [entityId], named [remoteName] or a free variant
         * of it (« Courses 2 »), and returns its id. Its items arrive with the next synchronisation. A
         * list [importedFromRemote] was added by the "all lists" mode: its name follows Home Assistant
         * and it leaves the phone with that mode.
         */
        suspend fun insertLinkedList(
            entityId: String,
            remoteName: String,
            importedFromRemote: Boolean,
        ): String {
            val lists = listDao.getAll()
            val tracked = trackedDao.getByEntityId(entityId)
            val now = clock.millis()
            val list =
                ShoppingListEntity(
                    localId = UUID.randomUUID().toString(),
                    name = HaListNameAllocator.uniqueName(remoteName, lists.map { it.name }),
                    isDefault = lists.none { it.isDefault },
                    createdAt = now,
                    updatedAt = now,
                    remoteId = entityId,
                    // A list this app created and that the user deletes is still deleted in Home Assistant.
                    remoteEntryId = tracked?.configEntryId,
                    createdByApp = tracked != null,
                    importedFromRemote = importedFromRemote,
                    remoteName = remoteName.takeIf { importedFromRemote },
                    syncStatus = SyncStatus.SYNCED,
                    position = listDao.nextPosition(),
                )
            listDao.insert(list)
            return list.localId
        }

        /** Stops synchronising the list; it stays on this phone with its items. */
        suspend fun unlink(list: ShoppingListEntity) {
            detachItems(list.localId, SyncStatus.LOCAL_ONLY)
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

        /**
         * The list's items forget their Home Assistant uid and take [status]; nothing queued for the
         * list is sent any more, and local deletions waiting to be sent are dropped.
         */
        suspend fun detachItems(
            listLocalId: String,
            status: SyncStatus,
        ) {
            queue.clearList(listLocalId)
            itemDao.deleteTombstones(listLocalId)
            itemDao.detachFromRemote(listLocalId, status)
        }

        // Only item operations name an item.
        suspend fun hasPendingItemChanges(listLocalId: String): Boolean = queue.pendingItemIds(listLocalId).isNotEmpty()

        /** The user parted with this Home Assistant list: the "all lists" mode must not bring it back. */
        suspend fun ignore(entityId: String) = ignoredDao.upsert(HaIgnoredListEntity(entityId, clock.millis()))

        suspend fun stopIgnoring(entityId: String) = ignoredDao.delete(entityId)

        suspend fun ignoredEntityIds(): Set<String> = ignoredDao.getEntityIds().toSet()
    }
