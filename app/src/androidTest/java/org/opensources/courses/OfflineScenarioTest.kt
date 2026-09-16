package org.opensources.courses

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.courses.core.model.SyncStatus
import org.opensources.courses.core.sync.SyncOperationType
import org.opensources.courses.feature.shopping.domain.NewShoppingItem
import org.opensources.courses.testing.TestRepositories

/**
 * Specification scenario 33, at data level: the database is closed and reopened to simulate the
 * app being closed. No component used here has any network dependency, so the behaviour is the
 * same with the network completely off.
 */
@RunWith(AndroidJUnit4::class)
class OfflineScenarioTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun cleanBefore() {
        context.deleteDatabase(DB_NAME)
    }

    @After
    fun cleanAfter() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun itemsAndChecksSurviveRestartsWithoutNetwork() =
        runTest {
            var repositories = TestRepositories.onDisk(context, DB_NAME)
            val list = repositories.lists.createList("Courses")
            repeat(10) { repositories.items.addItem(NewShoppingItem(list.id, "Article $it")) }
            repositories.database.close()

            repositories = TestRepositories.onDisk(context, DB_NAME)
            assertEquals(10, repositories.items.getItems(list.id).size)
            repeat(5) { repositories.items.addItem(NewShoppingItem(list.id, "Nouveau $it")) }
            repositories.items.getItems(list.id).take(3).forEach { repositories.items.setChecked(it.id, true) }
            repositories.database.close()

            repositories = TestRepositories.onDisk(context, DB_NAME)
            val items = repositories.items.getItems(list.id)
            assertEquals(15, items.size)
            assertEquals(3, items.count { it.isChecked })
            assertEquals(0, repositories.queue.pending().size)
            repositories.database.close()
        }

    @Test
    fun changesOnSynchronisedListStayQueuedAcrossRestarts() =
        runTest {
            var repositories = TestRepositories.onDisk(context, DB_NAME)
            val list = repositories.lists.createList("Courses")
            repositories.links.linkToExisting(list.id, "todo.courses")
            val milk = repositories.items.addItem(NewShoppingItem(list.id, "Lait"))
            repositories.items.addItem(NewShoppingItem(list.id, "Pain"))
            repositories.items.setChecked(milk.id, true)
            repositories.database.close()

            repositories = TestRepositories.onDisk(context, DB_NAME)
            assertEquals(
                listOf(SyncOperationType.CREATE_ITEM, SyncOperationType.CREATE_ITEM, SyncOperationType.CHECK_ITEM),
                repositories.queue.pending().map { it.type },
            )
            assertEquals(setOf(SyncStatus.PENDING), repositories.database.shoppingItemDao().getActiveForList(list.id).map { it.syncStatus }.toSet())
            repositories.database.close()
        }

    private companion object {
        const val DB_NAME = "offline-scenario-test.db"
    }
}
