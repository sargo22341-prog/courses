package org.opensources.courses.feature.catalog.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.courses.feature.catalog.domain.CatalogImportProduct
import org.opensources.courses.feature.catalog.domain.CatalogProductRef
import org.opensources.courses.feature.catalog.domain.CatalogSource
import org.opensources.courses.testing.TestRepositories

/**
 * What reading Mealie items needs from the catalog, on a real Room database: products found by alias,
 * and custom products left behind (a Mealie text read whole before) removed.
 */
@RunWith(AndroidJUnit4::class)
class CatalogCleanupAndAliasRoomTest {
    private val repositories = TestRepositories.inMemory(InstrumentationRegistry.getInstrumentation().targetContext)
    private val catalog = repositories.catalog

    @After
    fun close() {
        repositories.database.close()
    }

    private suspend fun exists(id: String) = catalog.findByIds(setOf(id)).isNotEmpty()

    @Test
    fun aCustomProductIsRemovedOnlyOnceNoItemAndNoAdditionRefersToIt() =
        runTest {
            val list = repositories.lists.createList("Mealie")
            val raw = catalog.getOrCreateCustomProduct("250 grammes Pâtes").id
            repositories.syncStore.insertRemoteItem(list.id, "uid-1", "250 grammes Pâtes", 1.0, null, false, raw)
            val typed = catalog.getOrCreateCustomProduct("Sauce maison").id
            catalog.recordUsage(typed)

            catalog.deleteUnusedCustomProduct(raw)
            assertTrue(exists(raw))

            val item = repositories.syncStore.items(list.id).single()
            repositories.syncStore.applyRemoteItem(item.localId, "Pâtes", 250.0, "g", false, null)
            catalog.deleteUnusedCustomProduct(raw)
            catalog.deleteUnusedCustomProduct(typed)

            assertFalse(exists(raw))
            assertTrue(exists(typed))
        }

    @Test
    fun aProductIsFoundByItsAliasWhichIsGivenAsItsName() =
        runTest {
            catalog.replaceCatalog(
                CatalogSource.OPEN_FOOD_FACTS,
                "off-fr",
                listOf(CatalogImportProduct("en:sesame", "Sésame", null, 1, aliases = listOf("Graines de sésame"))),
            )

            val found = catalog.findByNormalizedAliases(setOf("graines de sesame", "sesame"))

            assertEquals(listOf(CatalogProductRef("en:sesame", "graines de sesame", CatalogSource.OPEN_FOOD_FACTS)), found)
        }

    @Test
    fun productsOfTheCatalogAreNeverRemoved() =
        runTest {
            catalog.replaceCatalog(CatalogSource.SEED, "seed-3-fr", listOf(CatalogImportProduct("seed:pates", "Pâtes", null, 8)))

            catalog.deleteUnusedCustomProduct("seed:pates")

            assertTrue(exists("seed:pates"))
        }
}
