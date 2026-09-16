package org.opensources.courses.feature.homeassistant.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.courses.core.model.SyncStatus
import org.opensources.courses.core.sync.SyncOperationType
import org.opensources.courses.feature.shopping.domain.NewShoppingItem
import org.opensources.courses.testing.TestRepositories

/** Changes given up after repeated refusals, and a forgotten Home Assistant, on a real Room database. */
@RunWith(AndroidJUnit4::class)
class HaGivenUpChangesRoomTest {
    private val repositories = TestRepositories.inMemory(InstrumentationRegistry.getInstrumentation().targetContext)
    private val store = repositories.syncStore
    private val queue = repositories.queue
    private val itemDao = repositories.database.shoppingItemDao()
    private val listDao = repositories.database.shoppingListDao()

    @After
    fun close() {
        repositories.database.close()
    }

    /** An item of a linked list, known by Home Assistant, with nothing left to send. */
    private suspend fun syncedItem(name: String): String {
        store.importList("todo.courses", "Courses")
        val listId = listDao.getAll().single { it.remoteId == "todo.courses" }.localId
        val item = repositories.items.addItem(NewShoppingItem(listId, name))
        store.setItemRemoteId(item.id, "uid-$name")
        queue.complete(queue.pending().map { it.id })
        return item.id
    }

    @Test
    fun aDeletionGivenUpBringsTheItemBack() =
        runTest {
            val id = syncedItem("Pain")
            repositories.items.deleteItem(id)
            val deletion = queue.pending().single()
            assertEquals(SyncOperationType.DELETE_ITEM, deletion.type)

            store.abandonItemChanges(id, listOf(deletion.id))

            val item = itemDao.getById(id)
            assertFalse(item!!.isDeleted)
            assertEquals(SyncStatus.SYNCED, item.syncStatus)
            assertTrue(queue.pending().isEmpty())
        }

    @Test
    fun aCreationGivenUpLeavesTheItemOnThisPhoneOnly() =
        runTest {
            store.importList("todo.courses", "Courses")
            val listId = listDao.getAll().single().localId
            val item = repositories.items.addItem(NewShoppingItem(listId, "Lait"))

            store.abandonItemChanges(item.id, queue.pending().map { it.id })

            assertEquals(SyncStatus.LOCAL_ONLY, itemDao.getById(item.id)?.syncStatus)
            assertTrue(queue.pending().isEmpty())
        }

    @Test
    fun aChangeMadeDuringTheSynchronisationIsStillSent() =
        runTest {
            val id = syncedItem("Lait")
            repositories.items.setChecked(id, true)
            val refused = queue.pending().single()
            repositories.items.setChecked(id, false)

            store.abandonItemChanges(id, listOf(refused.id))

            assertEquals(SyncStatus.PENDING, itemDao.getById(id)?.syncStatus)
            assertEquals(listOf(SyncOperationType.UNCHECK_ITEM), queue.pending().map { it.type })
        }

    @Test
    fun forgettingHomeAssistantKeepsEveryListUnlinkedAndSendsNothing() =
        runTest {
            val mine = repositories.lists.createList("Maison")
            val tombstone = syncedItem("Pain")
            repositories.items.deleteItem(tombstone)
            store.importList("todo.bricolage", "Bricolage")
            repositories.lists.deleteList(listDao.getAll().single { it.remoteId == "todo.bricolage" }.localId)
            repositories.links.createInHomeAssistant(mine.id)
            assertTrue(queue.pending().map { it.type }.containsAll(listOf(SyncOperationType.DELETE_LIST, SyncOperationType.CREATE_LIST)))

            repositories.links.unlinkAll()

            val lists = listDao.getAll()
            assertEquals(setOf("Maison", "Courses"), lists.map { it.name }.toSet())
            assertTrue(lists.all { it.remoteId == null && it.syncStatus == SyncStatus.LOCAL_ONLY && !it.importedFromRemote })
            assertNull(itemDao.getById(tombstone))
            assertTrue(queue.pending().isEmpty())
            assertTrue(store.ignoredEntityIds().isEmpty())
        }
}
