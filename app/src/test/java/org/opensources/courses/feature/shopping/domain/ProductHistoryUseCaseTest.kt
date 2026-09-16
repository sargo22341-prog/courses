package org.opensources.courses.feature.shopping.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.opensources.courses.feature.catalog.domain.ProductSuggestion
import org.opensources.courses.testing.FakeCatalogRepository
import org.opensources.courses.testing.product

class ProductHistoryUseCaseTest {
    private val catalog = FakeCatalogRepository(listOf(product("Lait"), product("Pain"), product("Œufs"), product("Beurre")))
    private val history = ProductHistoryUseCase(catalog)

    private fun item(
        name: String,
        catalogProductId: String? = null,
    ) = ShoppingItem("item:$name", "list", name, 1.0, null, isChecked = false, catalogProductId = catalogProductId)

    private fun suggestion(
        name: String,
        id: String = "id:$name",
    ) = ProductSuggestion(id, name, null)

    @Test
    fun `the most added products come first and never added ones are left out`() =
        runTest {
            repeat(3) { catalog.recordUsage("id:Pain") }
            catalog.recordUsage("id:Lait")

            assertEquals(listOf("Pain", "Lait"), history.observeFrequent().first().map { it.name })
        }

    @Test
    fun `products already waiting in the list are left out, by product or by name`() {
        val frequent = listOf(suggestion("Pain"), suggestion("Lait"), suggestion("Œufs"), suggestion("Beurre"))
        // "LAIT" comes from Home Assistant, not linked to the catalog yet.
        val toBuy = listOf(item("Baguette", catalogProductId = "id:Pain"), item("LAIT"))

        assertEquals(listOf("Œufs", "Beurre"), history.notInList(frequent, toBuy).map { it.name })
    }

    @Test
    fun `a name known by several catalog sources is shown once`() {
        val frequent = listOf(suggestion("Lait", id = "seed:lait"), suggestion("lait", id = "custom:lait"), suggestion("Pain"))

        assertEquals(listOf("seed:lait", "id:Pain"), history.notInList(frequent, emptyList()).map { it.productId })
    }

    @Test
    fun `the history is limited once the listed products are removed`() {
        val frequent = listOf(suggestion("Pain"), suggestion("Lait"), suggestion("Œufs"))

        assertEquals(listOf("Lait", "Œufs"), history.notInList(frequent, listOf(item("Pain")), limit = 2).map { it.name })
    }
}
