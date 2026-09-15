package org.opensources.courses.feature.shopping.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.opensources.courses.core.model.SyncStatus
import org.opensources.courses.feature.catalog.domain.GroceryCategory
import org.opensources.courses.feature.language.domain.AppLanguage
import org.opensources.courses.testing.FakeAppLanguageRepository
import org.opensources.courses.testing.FakeCatalogRepository

class GroupItemsByCategoryUseCaseTest {
    private val catalog =
        FakeCatalogRepository().apply {
            categories["lait"] = GroceryCategory.DAIRY_EGGS
            categories["yaourts"] = GroceryCategory.DAIRY_EGGS
            categories["tomates"] = GroceryCategory.FRUITS_VEGETABLES
            categories["pains"] = GroceryCategory.BAKERY
        }
    private val languages = FakeAppLanguageRepository(AppLanguage.FRENCH)
    private val group = GroupItemsByCategoryUseCase(catalog, languages)

    private fun item(
        name: String,
        catalogProductId: String? = null,
    ) = ShoppingItem("id-$name", "list", name, 1.0, null, false, catalogProductId, 0, 0, SyncStatus.LOCAL_ONLY)

    @Test
    fun `sections follow the store order with unknown names last`() =
        runTest {
            val sections = group(listOf(item("Sauce maison"), item("Lait"), item("Pain"), item("Tomates"))).first()

            assertEquals(
                listOf(GroceryCategory.FRUITS_VEGETABLES, GroceryCategory.BAKERY, GroceryCategory.DAIRY_EGGS, GroceryCategory.OTHER),
                sections.map { it.category },
            )
            assertEquals(listOf("Sauce maison"), sections.last().items.map { it.name })
        }

    @Test
    fun `names written differently than in the catalog still find their section`() =
        runTest {
            // As they may come from Home Assistant: other case, singular, extra spaces.
            val sections = group(listOf(item("TOMATE"), item(" yaourt "))).first()

            assertEquals(listOf(GroceryCategory.FRUITS_VEGETABLES, GroceryCategory.DAIRY_EGGS), sections.map { it.category })
        }

    @Test
    fun `items keep their order inside a section`() =
        runTest {
            val sections = group(listOf(item("Yaourts"), item("Pain"), item("Lait"))).first()

            assertEquals(listOf("Yaourts", "Lait"), sections.first { it.category == GroceryCategory.DAIRY_EGGS }.items.map { it.name })
        }

    @Test
    fun `an item written in the previous language keeps the section of its linked product`() =
        runTest {
            languages.setLanguage(AppLanguage.ENGLISH)
            catalog.categories.clear()
            catalog.categoriesById["seed:lait"] = GroceryCategory.DAIRY_EGGS

            val sections = group(listOf(item("Lait", catalogProductId = "seed:lait"), item("Lait", catalogProductId = null).copy(id = "unlinked"))).first()

            assertEquals(listOf(GroceryCategory.DAIRY_EGGS, GroceryCategory.OTHER), sections.map { it.category })
        }

    @Test
    fun `the name wins over the link`() =
        runTest {
            catalog.categoriesById["seed:lait"] = GroceryCategory.DAIRY_EGGS

            val sections = group(listOf(item("Pain", catalogProductId = "seed:lait"))).first()

            assertEquals(listOf(GroceryCategory.BAKERY), sections.map { it.category })
        }

    @Test
    fun `plural forms follow the app language`() =
        runTest {
            languages.setLanguage(AppLanguage.ITALIAN)
            catalog.categories["pomodori"] = GroceryCategory.FRUITS_VEGETABLES

            val sections = group(listOf(item("Pomodoro"))).first()

            assertEquals(listOf(GroceryCategory.FRUITS_VEGETABLES), sections.map { it.category })
        }
}
