package org.opensources.courses.feature.shopping.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.opensources.courses.testing.FakeCatalogRepository
import org.opensources.courses.testing.FakeShoppingItemRepository
import org.opensources.courses.testing.product

class AddItemUseCaseTest {
    private val items = FakeShoppingItemRepository()
    private val catalog = FakeCatalogRepository(listOf(product("Lait")))
    private val addItem = AddItemUseCase(items, catalog)

    @Test
    fun `adds a catalog product and records its usage`() =
        runTest {
            val added = addItem("list", "Lait", "id:Lait")

            assertEquals("Lait", added?.name)
            assertEquals("id:Lait", added?.catalogProductId)
            assertEquals(1, catalog.usage["id:Lait"])
        }

    @Test
    fun `free text becomes a custom product suggested next time`() =
        runTest {
            addItem("list", "  Sauce   piquante ")

            assertEquals("Sauce piquante", items.getItems("list").single().name)
            val custom = catalog.findCandidates("sauce", 10).single()
            assertEquals("Sauce piquante", custom.product.name)
            assertEquals(1, catalog.usage[custom.product.id])
        }

    @Test
    fun `adding a product already waiting increments its quantity`() =
        runTest {
            addItem("list", "Lait", "id:Lait")
            addItem("list", "lait", "id:Lait")

            val item = items.getItems("list").single()
            assertEquals(2.0, item.quantity, 0.0)
            assertEquals(2, catalog.usage["id:Lait"])
        }

    @Test
    fun `adding again a product measured with a unit leaves its quantity`() =
        runTest {
            val first = addItem("list", "Farine")!!
            items.updateItem(first.id, "Farine", 1.5, "kg")

            addItem("list", "farine")

            val item = items.getItems("list").single()
            assertEquals(1.5, item.quantity, 0.0)
            assertEquals("kg", item.unit)
            assertEquals(2, catalog.usage[first.catalogProductId])
        }

    @Test
    fun `adding a purchased product puts it back to buy`() =
        runTest {
            val first = addItem("list", "Lait", "id:Lait")!!
            items.setChecked(first.id, true)

            addItem("list", "Lait", "id:Lait")

            assertFalse(items.getItems("list").single().isChecked)
        }

    @Test
    fun `blank names are ignored`() =
        runTest {
            assertNull(addItem("list", "   "))
            assertEquals(0, items.getItems("list").size)
        }
}
