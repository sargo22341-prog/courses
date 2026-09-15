package org.opensources.courses.feature.shopping.domain

import kotlinx.coroutines.flow.Flow

interface ShoppingItemRepository {
    fun observeItems(listId: String): Flow<List<ShoppingItem>>

    suspend fun getItems(listId: String): List<ShoppingItem>

    /** The items of every list. */
    suspend fun getAllItems(): List<ShoppingItem>

    suspend fun addItem(item: NewShoppingItem): ShoppingItem

    /** Persists a new name, quantity and unit for an existing item; a new name drops its catalog link. */
    suspend fun updateItem(
        itemId: String,
        name: String,
        quantity: Double,
        unit: String?,
    )

    /**
     * Links an item to the catalog product it stands for, unless it was renamed since it was read
     * as [expectedName]. Local data only: never synchronised. Returns whether the item was linked.
     */
    suspend fun setCatalogProduct(
        itemId: String,
        expectedName: String,
        catalogProductId: String,
    ): Boolean

    suspend fun setChecked(
        itemId: String,
        checked: Boolean,
    )

    suspend fun deleteItem(itemId: String)

    /** Deletes every checked item of [listId]; returns how many were removed. */
    suspend fun deletePurchased(listId: String): Int
}
