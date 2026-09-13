package org.opensources.courses.feature.shopping.domain

import kotlinx.coroutines.flow.Flow

interface ShoppingItemRepository {
    fun observeItems(listId: String): Flow<List<ShoppingItem>>

    suspend fun getItems(listId: String): List<ShoppingItem>

    suspend fun addItem(item: NewShoppingItem): ShoppingItem

    /** Persists a new name, quantity and unit for an existing item. */
    suspend fun updateItem(
        itemId: String,
        name: String,
        quantity: Double,
        unit: String?,
    )

    suspend fun setChecked(
        itemId: String,
        checked: Boolean,
    )

    suspend fun deleteItem(itemId: String)

    /** Deletes every checked item of [listId]; returns how many were removed. */
    suspend fun deletePurchased(listId: String): Int
}
