package org.opensources.courses.feature.homeassistant.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.courses.core.model.SyncStatus
import org.opensources.courses.core.sync.SyncOperationType
import org.opensources.courses.feature.shopping.domain.NewShoppingItem
import org.opensources.courses.testing.TestRepositories

/** What linking, unlinking and emptying a linked list do to its items, on a real Room database. */
@RunWith(AndroidJUnit4::class)
class HaListLinkRoomTest {
    private val repositories = TestRepositories.inMemory(InstrumentationRegistry.getInstrumentation().targetContext)
    private val store = repositories.syncStore
    private val queue = repositories.queue
    private val itemDao = repositories.database.shoppingItemDao()

    @After
    fun close() {
        repositories.database.close()
    }

    /** A list linked to `todo.courses` holding [names], all known by Home Assistant, with nothing left to send. */
    private suspend fun linkedList(vararg names: String): Pair<String, List<String>> {
        val list = repositories.lists.createList("Courses")
        repositories.links.linkToExisting(list.id, "todo.courses")
        val ids =
            names.map { name ->
                repositories.items.addItem(NewShoppingItem(list.id, name)).id.also { store.setItemRemoteId(it, "uid-$name") }
            }
        queue.complete(queue.pending().map { it.id })
        return list.id to ids
    }

    @Test
    fun unlinkingKeepsTheItemsWithoutUidAndDropsTheDeletionsNotSent() =
        runTest {
            val (listId, ids) = linkedList("Lait", "Pain")
            repositories.items.deleteItem(ids[1])

            repositories.links.unlink(listId)

            val milk = itemDao.getById(ids[0])
            assertNull(milk?.remoteId)
            assertEquals(SyncStatus.LOCAL_ONLY, milk?.syncStatus)
            assertNull(itemDao.getById(ids[1]))
            assertTrue(queue.pending().isEmpty())
        }

    @Test
    fun linkingToAnotherListSendsEveryItemAgain() =
        runTest {
            val (listId, ids) = linkedList("Lait", "Pain")
            repositories.items.deleteItem(ids[1])

            repositories.links.linkToExisting(listId, "todo.maison")

            val milk = itemDao.getById(ids[0])
            assertNull(milk?.remoteId)
            assertEquals(SyncStatus.PENDING, milk?.syncStatus)
            assertNull(itemDao.getById(ids[1]))
            assertEquals(listOf(SyncOperationType.CREATE_ITEM to ids[0]), queue.pending().map { it.type to it.itemLocalId })
        }

    @Test
    fun purchasedItemsOfALinkedListAreDeletedInHomeAssistantOnlyWhenKnownThere() =
        runTest {
            val (listId, ids) = linkedList("Lait", "Pain")
            val eggs = repositories.items.addItem(NewShoppingItem(listId, "Œufs")).id
            queue.complete(queue.pending().map { it.id })
            listOf(ids[0], eggs).forEach { repositories.items.setChecked(it, true) }
            queue.complete(queue.pending().map { it.id })

            assertEquals(2, repositories.items.deletePurchased(listId))

            assertTrue(itemDao.getById(ids[0])?.isDeleted == true)
            assertNull(itemDao.getById(eggs))
            assertEquals(listOf("Pain"), repositories.items.getItems(listId).map { it.name })
            val deletion = queue.pending().single()
            assertEquals(SyncOperationType.DELETE_ITEM, deletion.type)
            assertEquals("uid-Lait", deletion.remoteItemId)
        }

    @Test
    fun changesAppliedTogetherInOneTransactionAreAllWritten() =
        runTest {
            val (listId, ids) = linkedList("Lait")

            store.inTransaction {
                store.applyRemoteItem(ids[0], "Lait entier", 2.0, "L", checked = true)
                store.insertRemoteItem(listId, "uid-Pain", "Pain", 1.0, null, checked = false, catalogProductId = null)
            }

            val items = itemDao.getAllForList(listId).associateBy { it.name }
            assertEquals(2.0, items.getValue("Lait entier").quantity, 0.0)
            assertTrue(items.getValue("Lait entier").isChecked)
            assertEquals("uid-Pain", items.getValue("Pain").remoteId)
        }
}
