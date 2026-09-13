package org.opensources.courses.feature.shopping.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.opensources.courses.core.model.SyncStatus
import org.opensources.courses.testing.FakeCatalogRepository
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

class FakeShoppingItemRepository : ShoppingItemRepository {
    private val state = MutableStateFlow<List<ShoppingItem>>(emptyList())
    private var nextId = 1

    override fun observeItems(listId: String): Flow<List<ShoppingItem>> = state.map { all -> all.filter { it.listId == listId } }

    override suspend fun getItems(listId: String): List<ShoppingItem> = state.value.filter { it.listId == listId }

    override suspend fun addItem(item: NewShoppingItem): ShoppingItem {
        val created =
            ShoppingItem("item${nextId++}", item.listId, item.name, item.quantity, item.unit, false, item.catalogProductId, 0, 0, SyncStatus.LOCAL_ONLY)
        state.value = state.value + created
        return created
    }

    override suspend fun updateItem(
        itemId: String,
        name: String,
        quantity: Double,
        unit: String?,
    ) = change(itemId) { it.copy(name = name, quantity = quantity, unit = unit) }

    override suspend fun setChecked(
        itemId: String,
        checked: Boolean,
    ) = change(itemId) { it.copy(isChecked = checked) }

    override suspend fun deleteItem(itemId: String) {
        state.value = state.value.filterNot { it.id == itemId }
    }

    override suspend fun deletePurchased(listId: String): Int {
        val purchased = state.value.filter { it.listId == listId && it.isChecked }
        state.value = state.value - purchased.toSet()
        return purchased.size
    }

    private fun change(
        itemId: String,
        block: (ShoppingItem) -> ShoppingItem,
    ) {
        state.value = state.value.map { if (it.id == itemId) block(it) else it }
    }
}
