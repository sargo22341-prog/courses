package org.opensources.courses.feature.shopping.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.opensources.courses.feature.catalog.domain.CatalogSource
import org.opensources.courses.feature.language.domain.AppLanguage
import org.opensources.courses.testing.FakeAppLanguageRepository
import org.opensources.courses.testing.FakeCatalogRepository
import org.opensources.courses.testing.FakeShoppingItemRepository
import org.opensources.courses.testing.product

class LinkItemsToCatalogUseCaseTest {
    private val items = FakeShoppingItemRepository()
    private val catalog = FakeCatalogRepository()
    private val languages = FakeAppLanguageRepository(AppLanguage.ENGLISH)
    private val link = LinkItemsToCatalogUseCase(items, catalog, languages)

    private suspend fun add(
        name: String,
        catalogProductId: String?,
        listId: String = "list",
    ) = items.addItem(NewShoppingItem(listId, name, catalogProductId = catalogProductId))

    private suspend fun linkOf(itemId: String) = items.getAllItems().single { it.id == itemId }.catalogProductId

    @Test
    fun `an item written before the language change keeps its product, now named in the new language`() =
        runTest {
            catalog.candidates += product("Milk", id = "seed:lait")
            val milk = add("Lait", "seed:lait")

            assertEquals(0, link())
            assertEquals("seed:lait", linkOf(milk.id))
            assertEquals("Lait", items.getAllItems().single().name)
        }

    @Test
    fun `items of every list are linked to the product carrying their name, singular or plural`() =
        runTest {
            catalog.candidates += product("Tomatoes", id = "en:tomatoes", source = CatalogSource.OPEN_FOOD_FACTS)
            catalog.candidates += product("Tomato", id = "custom:tomato", source = CatalogSource.CUSTOM)
            val typed = add("Tomato", "custom:tomato")
            val fromOtherList = add("tomatoes", null, listId = "other")

            assertEquals(2, link())
            assertEquals("en:tomatoes", linkOf(typed.id))
            assertEquals("en:tomatoes", linkOf(fromOtherList.id))
        }

    @Test
    fun `an unknown item or one whose product disappeared becomes a custom product`() =
        runTest {
            val unknown = add("Homemade sauce", null)
            val dangling = add("Old product", "en:removed")

            assertEquals(2, link())
            assertEquals("custom:homemade sauce", linkOf(unknown.id))
            assertEquals("custom:old product", linkOf(dangling.id))
        }

    @Test
    fun `linking twice changes nothing the second time`() =
        runTest {
            catalog.candidates += product("Bread", id = "seed:pain")
            add("bread", null)

            assertEquals(1, link())
            assertEquals(0, link())
        }
}
