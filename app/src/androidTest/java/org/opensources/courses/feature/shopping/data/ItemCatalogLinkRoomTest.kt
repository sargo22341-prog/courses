package org.opensources.courses.feature.shopping.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.courses.feature.catalog.domain.CatalogImportProduct
import org.opensources.courses.feature.catalog.domain.CatalogProductRef
import org.opensources.courses.feature.catalog.domain.CatalogSource
import org.opensources.courses.feature.catalog.domain.GroceryCategory
import org.opensources.courses.feature.language.domain.AppLanguage
import org.opensources.courses.feature.language.domain.AppLanguageRepository
import org.opensources.courses.feature.shopping.domain.LinkItemsToCatalogUseCase
import org.opensources.courses.feature.shopping.domain.NewShoppingItem
import org.opensources.courses.testing.TestRepositories

/** A language change on a real database: the catalog is replaced, list items stay and follow their products. */
@RunWith(AndroidJUnit4::class)
class ItemCatalogLinkRoomTest {
    private val repositories = TestRepositories.inMemory(InstrumentationRegistry.getInstrumentation().targetContext)
    private val english =
        object : AppLanguageRepository {
            override val language = MutableStateFlow(AppLanguage.ENGLISH)

            override fun hasChosenLanguage() = true

            override fun setLanguage(language: AppLanguage) = Unit

            override fun refresh() = Unit
        }

    @After
    fun close() {
        repositories.database.close()
    }

    private fun seedMilk(name: String) = CatalogImportProduct("seed:lait", name, null, 8, groceryCategory = GroceryCategory.DAIRY_EGGS)

    @Test
    fun replacingTheCatalogInAnotherLanguageKeepsListItemsAndTheirProducts() =
        runTest {
            repositories.catalog.replaceSeedCatalog("seed-3-fr", listOf(seedMilk("Lait")))
            val list = repositories.lists.createList("Courses")
            val item = repositories.items.addItem(NewShoppingItem(list.id, "Lait", catalogProductId = "seed:lait"))
            repositories.catalog.recordUsage("seed:lait")

            repositories.catalog.replaceSeedCatalog("seed-3-en", listOf(seedMilk("Milk")))
            LinkItemsToCatalogUseCase(repositories.items, repositories.catalog, english)()

            val stored = repositories.items.getItems(list.id).single()
            assertEquals(item.id, stored.id)
            assertEquals("Lait", stored.name)
            assertEquals("seed:lait", stored.catalogProductId)
            assertEquals(listOf("Milk"), repositories.catalog.findCandidates("milk", 10).map { it.product.name })
            assertEquals(1, repositories.catalog.findCandidates("milk", 10).single().useCount)
            assertEquals(mapOf("seed:lait" to GroceryCategory.DAIRY_EGGS), repositories.catalog.observeCategoriesByIds(setOf("seed:lait")).first())
        }

    @Test
    fun productsAreFoundByNameAndById() =
        runTest {
            repositories.catalog.replaceSeedCatalog("seed-3-en", listOf(seedMilk("Milk")))
            val custom = repositories.catalog.getOrCreateCustomProduct("Homemade sauce")

            assertEquals(listOf(CatalogProductRef("seed:lait", "milk", CatalogSource.SEED)), repositories.catalog.findByNormalizedNames(setOf("milk", "bread")))
            assertEquals(setOf("seed:lait", custom.id), repositories.catalog.findByIds(setOf("seed:lait", custom.id, "en:removed")).map { it.id }.toSet())
        }

    @Test
    fun linkingIsLocalAndNeverOverwritesAnItemRenamedMeanwhile() =
        runTest {
            val list = repositories.lists.createList("Courses")
            repositories.links.linkToExisting(list.id, "todo.courses")
            val item = repositories.items.addItem(NewShoppingItem(list.id, "Lait"))
            val queued = repositories.queue.pending().size

            assertTrue(repositories.items.setCatalogProduct(item.id, "Lait", "seed:lait"))
            assertFalse(repositories.items.setCatalogProduct(item.id, "Old name", "en:milks"))

            assertEquals("seed:lait", repositories.items.getItems(list.id).single().catalogProductId)
            assertEquals(queued, repositories.queue.pending().size)
        }

    @Test
    fun renamingAnItemDropsItsLinkUnlessOnlyTheCaseChanged() =
        runTest {
            val list = repositories.lists.createList("Courses")
            val item = repositories.items.addItem(NewShoppingItem(list.id, "Lait", catalogProductId = "seed:lait"))

            repositories.items.updateItem(item.id, "LAIT", 1.0, null)
            assertEquals("seed:lait", repositories.items.getItems(list.id).single().catalogProductId)

            repositories.items.updateItem(item.id, "Piles", 1.0, null)
            assertNull(repositories.items.getItems(list.id).single().catalogProductId)
        }
}
