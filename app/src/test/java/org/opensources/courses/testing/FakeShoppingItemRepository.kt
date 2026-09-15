package org.opensources.courses.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.opensources.courses.core.model.SyncStatus
import org.opensources.courses.feature.shopping.domain.NewShoppingItem
import org.opensources.courses.feature.shopping.domain.ShoppingItem
import org.opensources.courses.feature.shopping.domain.ShoppingItemRepository

class FakeShoppingItemRepository : ShoppingItemRepository {
    private val state = MutableStateFlow<List<ShoppingItem>>(emptyList())
    private var nextId = 1

    override fun observeItems(listId: String): Flow<List<ShoppingItem>> = state.map { all -> all.filter { it.listId == listId } }

    override suspend fun getItems(listId: String): List<ShoppingItem> = state.value.filter { it.listId == listId }

    override suspend fun getAllItems(): List<ShoppingItem> = state.value

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

    /** Same guard as Room: an item renamed since it was read is left alone. */
    override suspend fun setCatalogProduct(
        itemId: String,
        expectedName: String,
        catalogProductId: String,
    ): Boolean {
        val item = state.value.firstOrNull { it.id == itemId && it.name == expectedName } ?: return false
        change(item.id) { it.copy(catalogProductId = catalogProductId) }
        return true
    }

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
