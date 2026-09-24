package org.opensources.courses.feature.catalog.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.opensources.courses.testing.FakeAppLanguageRepository
import org.opensources.courses.testing.FakeCatalogRepository
import org.opensources.courses.testing.product

class FindProductInTextUseCaseTest {
    private val catalog =
        FakeCatalogRepository(
            listOf(
                product("Ail", id = "seed:ail"),
                product("Oignons", id = "off:oignons", source = CatalogSource.OPEN_FOOD_FACTS),
                product("Oignons rouges", id = "off:oignons-rouges", source = CatalogSource.OPEN_FOOD_FACTS),
                product("Sésame", id = "off:sesame", aliases = listOf("Graines de sésame"), source = CatalogSource.OPEN_FOOD_FACTS),
                product("Huile de sésame", id = "off:huile-de-sesame", source = CatalogSource.OPEN_FOOD_FACTS),
                product("Curry", id = "seed:curry"),
                product("Poudre de curry", id = "off:poudre", aliases = listOf("Curry"), source = CatalogSource.OPEN_FOOD_FACTS),
                product("Crevettes", id = "off:crevettes", source = CatalogSource.OPEN_FOOD_FACTS),
                product("Crevettes", id = "seed:crevettes"),
                product("Rouge", id = "custom:rouge", source = CatalogSource.CUSTOM),
            ),
        )
    private val find = FindProductInTextUseCase(catalog, FakeAppLanguageRepository())

    @Test
    fun `the food named inside a recipe text is found`() =
        runTest {
            assertEquals("seed:ail", find("gousse ail"))
            assertEquals("off:sesame", find("graines de sésame ou selon le goût"))
        }

    @Test
    fun `aliases name products too, but a product's own name wins`() =
        runTest {
            assertEquals("off:sesame", find("graines de sésame"))
            assertEquals("seed:curry", find("1 curry"))
        }

    @Test
    fun `the longest name wins, singular or plural`() =
        runTest {
            assertEquals("off:oignons-rouges", find("oignon rouge"))
            assertEquals("off:oignons", find("oignon émincé"))
        }

    @Test
    fun `for one name the bundled catalog wins over OpenFoodFacts`() =
        runTest {
            assertEquals("seed:crevettes", find("crevettes décortiquées"))
        }

    @Test
    fun `custom products and linking words are never picked`() =
        runTest {
            assertNull(find("vin rouge"))
            assertNull(find("de la"))
            assertNull(find(""))
        }

    @Test
    fun `runs of words are tried longest first, then earliest, without linking words at their ends`() {
        assertEquals(listOf("graines de sesame", "graines", "sesame"), ProductNameWindows.of("graines de sesame"))
        assertEquals(listOf("oignon rouge", "oignon", "rouge"), ProductNameWindows.of("oignon rouge"))
        assertEquals(listOf("pates"), ProductNameWindows.of("250 pates"))
    }
}
