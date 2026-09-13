package org.opensources.courses.feature

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
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
import org.opensources.courses.feature.catalog.domain.CatalogImportProduct
import org.opensources.courses.feature.shopping.domain.NewShoppingItem
import org.opensources.courses.testing.TestRepositories

@RunWith(AndroidJUnit4::class)
class RoomRepositoriesTest {
    private val repositories = TestRepositories.inMemory(InstrumentationRegistry.getInstrumentation().targetContext)

    @After
    fun close() {
        repositories.database.close()
    }

    @Test
    fun localListChangesAreNeverQueued() =
        runTest {
            val list = repositories.lists.createList("Courses")
            val item = repositories.items.addItem(NewShoppingItem(list.id, "Lait"))
            repositories.items.setChecked(item.id, true)
            repositories.items.deleteItem(item.id)

            assertTrue(repositories.items.getItems(list.id).isEmpty())
            assertTrue(repositories.queue.pending().isEmpty())
        }

    @Test
    fun synchronisedListQueuesCreateCheckUncheckAndUpdate() =
        runTest {
            val list = repositories.lists.createList("Courses")
            repositories.links.linkToExisting(list.id, "todo.courses")
            val item = repositories.items.addItem(NewShoppingItem(list.id, "Lait"))
            repositories.items.setChecked(item.id, true)
            repositories.items.setChecked(item.id, false)
            repositories.items.updateItem(item.id, "Lait entier", 2.0, null)

            assertEquals(
                listOf(SyncOperationType.CREATE_ITEM, SyncOperationType.CHECK_ITEM, SyncOperationType.UNCHECK_ITEM, SyncOperationType.UPDATE_ITEM),
                repositories.queue.pending().map { it.type },
            )
            val stored = repositories.items.getItems(list.id).single()
            assertEquals(SyncStatus.PENDING, stored.syncStatus)
            assertEquals(2.0, stored.quantity, 0.0)
        }

    @Test
    fun newListIsQueuedForCreationWhenAutomaticCreationIsOn() =
        runTest {
            repositories.remoteSync.newListsSynchronized = true

            val list = repositories.lists.createList("BBQ")
            repositories.items.addItem(NewShoppingItem(list.id, "Merguez"))

            assertEquals(SyncStatus.PENDING, list.syncStatus)
            assertTrue(list.createdByApp)
            assertEquals(listOf(SyncOperationType.CREATE_LIST, SyncOperationType.CREATE_ITEM), repositories.queue.pending().map { it.type })
        }

    @Test
    fun deletingSynchronisedItemKeepsHiddenTombstoneUntilSynced() =
        runTest {
            val list = repositories.lists.createList("Courses")
            repositories.links.linkToExisting(list.id, "todo.courses")
            val item = repositories.items.addItem(NewShoppingItem(list.id, "Pain"))
            val dao = repositories.database.shoppingItemDao()
            dao.update(dao.getById(item.id)!!.copy(remoteId = "uid-1"))

            repositories.items.deleteItem(item.id)

            assertTrue(repositories.items.observeItems(list.id).first().isEmpty())
            assertTrue(dao.getById(item.id)!!.isDeleted)
            val deletion = repositories.queue.pending().last()
            assertEquals(SyncOperationType.DELETE_ITEM, deletion.type)
            assertEquals("uid-1", deletion.remoteItemId)
        }

    @Test
    fun deletePurchasedRemovesOnlyCheckedItems() =
        runTest {
            val list = repositories.lists.createList("Courses")
            val milk = repositories.items.addItem(NewShoppingItem(list.id, "Lait"))
            repositories.items.addItem(NewShoppingItem(list.id, "Pain"))
            repositories.items.setChecked(milk.id, true)

            assertEquals(1, repositories.items.deletePurchased(list.id))
            assertEquals(listOf("Pain"), repositories.items.getItems(list.id).map { it.name })
        }

    @Test
    fun lastListCannotBeDeletedAndDefaultMovesToRemainingList() =
        runTest {
            val first = repositories.lists.createList("Courses")
            val second = repositories.lists.createList("BBQ")
            assertTrue(repositories.lists.observeDefaultList().first()!!.id == first.id)

            assertTrue(repositories.lists.deleteList(first.id))
            assertEquals(second.id, repositories.lists.observeDefaultList().first()!!.id)
            assertFalse(repositories.lists.deleteList(second.id))
        }

    @Test
    fun catalogSearchUsesNamesAliasesAndKeepsUsageAcrossImports() =
        runTest {
            val milk = CatalogImportProduct("en:milks", "Laits", "Produits laitiers", null, 3, aliases = listOf("lolo"))
            repositories.catalog.replaceRemoteCatalog("v1", listOf(milk, CatalogImportProduct("en:breads", "Pains", null, null, 3)))
            repositories.catalog.recordUsage("en:milks")

            assertEquals(listOf("Laits"), repositories.catalog.findCandidates("lai", 10).map { it.product.name })
            assertEquals(listOf("Laits"), repositories.catalog.findCandidates("lolo", 10).map { it.product.name })

            repositories.catalog.replaceRemoteCatalog("v2", listOf(milk))

            val candidates = repositories.catalog.findCandidates("ai", 10)
            assertEquals(listOf("Laits"), candidates.map { it.product.name })
            assertEquals(1, candidates.single().useCount)
            assertNull(repositories.catalog.findCandidates("pain", 10).firstOrNull())
        }

    @Test
    fun customProductsAreCreatedOnce() =
        runTest {
            val first = repositories.catalog.getOrCreateCustomProduct("Sauce piquante")
            val second = repositories.catalog.getOrCreateCustomProduct("sauce PIQUANTE")

            assertEquals(first.id, second.id)
            assertEquals("Sauce piquante", repositories.catalog.findCandidates("piquante", 10).single().product.name)
        }
}
