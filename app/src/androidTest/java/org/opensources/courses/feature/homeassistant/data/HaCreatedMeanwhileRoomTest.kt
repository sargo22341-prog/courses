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
import org.opensources.courses.core.sync.SyncOperationType
import org.opensources.courses.feature.shopping.domain.NewShoppingItem
import org.opensources.courses.testing.TestRepositories

/** What was deleted in the app while Home Assistant created it, on a real Room database. */
@RunWith(AndroidJUnit4::class)
class HaCreatedMeanwhileRoomTest {
    private val repositories = TestRepositories.inMemory(InstrumentationRegistry.getInstrumentation().targetContext)
    private val store = repositories.syncStore
    private val queue = repositories.queue
    private val itemDao = repositories.database.shoppingItemDao()
    private val listDao = repositories.database.shoppingListDao()

    @After
    fun close() {
        repositories.database.close()
    }

    @Test
    fun anItemDeletedWhileCreatedComesBackAsATombstoneToDelete() =
        runTest {
            store.importList("todo.courses", "Courses")
            val listId = listDao.getAll().single().localId
            val kept = repositories.items.addItem(NewShoppingItem(listId, "Pain"))
            val deleted = repositories.items.addItem(NewShoppingItem(listId, "Lait"))
            val sent = store.items(listId).associateBy { it.localId }
            repositories.items.deleteItem(deleted.id)

            assertTrue(store.linkCreatedItem(sent.getValue(kept.id), "uid-pain"))
            assertFalse(store.linkCreatedItem(sent.getValue(deleted.id), "uid-lait"))

            assertEquals("uid-pain", itemDao.getById(kept.id)?.remoteId)
            val tombstone = itemDao.getById(deleted.id)
            assertTrue(tombstone!!.isDeleted)
            assertEquals("uid-lait", tombstone.remoteId)
            assertEquals(listOf(kept.id), itemDao.getActiveForList(listId).map { it.localId })
            val deletion = queue.pending().last()
            assertEquals(SyncOperationType.DELETE_ITEM to "uid-lait", deletion.type to deletion.remoteItemId)
        }

    @Test
    fun aListDeletedWhileCreatedHasTheNewListDeleted() =
        runTest {
            val kept = repositories.lists.createList("Courses")
            val deleted = repositories.lists.createList("BBQ")
            repositories.lists.deleteList(deleted.id)

            assertTrue(store.linkCreatedList(kept.id, "todo.courses", "entry-courses"))
            assertFalse(store.linkCreatedList(deleted.id, "todo.bbq", "entry-bbq"))

            assertEquals("todo.courses", listDao.getById(kept.id)?.remoteId)
            assertNull(repositories.database.haTrackedListDao().getByEntityId("todo.bbq"))
            val deletion = queue.pending().single()
            assertEquals(SyncOperationType.DELETE_LIST, deletion.type)
            assertEquals("todo.bbq" to "entry-bbq", deletion.remoteListId to deletion.remoteEntryId)
        }
}
