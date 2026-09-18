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
    fun `a typed count is the quantity of a new item`() =
        runTest {
            val added = addItem("list", "Pain", quantity = 2.0)

            assertEquals(2.0, added?.quantity ?: 0.0, 0.0)
            assertNull(added?.unit)
        }

    @Test
    fun `a typed measure is kept with its unit`() =
        runTest {
            addItem("list", "Pâtes", quantity = 500.0, unit = "g")

            val item = items.getItems("list").single()
            assertEquals(500.0, item.quantity, 0.0)
            assertEquals("g", item.unit)
        }

    @Test
    fun `a typed quantity adds up with the one waiting in the same unit`() =
        runTest {
            addItem("list", "Pain")
            addItem("list", "pain", quantity = 2.0)
            addItem("list", "Pâtes", quantity = 500.0, unit = "g")
            addItem("list", "pâtes", quantity = 250.0, unit = "G")

            val (bread, pasta) = items.getItems("list")
            assertEquals(3.0, bread.quantity, 0.0)
            assertEquals(750.0, pasta.quantity, 0.0)
        }

    @Test
    fun `a typed quantity in another unit replaces the one waiting`() =
        runTest {
            addItem("list", "Farine", quantity = 500.0, unit = "g")
            addItem("list", "Farine", quantity = 1.0, unit = "kg")

            val item = items.getItems("list").single()
            assertEquals(1.0, item.quantity, 0.0)
            assertEquals("kg", item.unit)
        }

    @Test
    fun `a purchased product added again with a quantity takes that quantity`() =
        runTest {
            val first = addItem("list", "Lait", "id:Lait", quantity = 3.0)!!
            items.setChecked(first.id, true)

            addItem("list", "Lait", "id:Lait", quantity = 2.0)

            val item = items.getItems("list").single()
            assertFalse(item.isChecked)
            assertEquals(2.0, item.quantity, 0.0)
        }

    @Test
    fun `blank names are ignored`() =
        runTest {
            assertNull(addItem("list", "   "))
            assertEquals(0, items.getItems("list").size)
        }
}
