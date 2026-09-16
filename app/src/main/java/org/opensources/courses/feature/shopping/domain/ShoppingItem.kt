package org.opensources.courses.feature.shopping.domain

data class ShoppingItem(
    val id: String,
    val listId: String,
    val name: String,
    val quantity: Double,
    val unit: String?,
    val isChecked: Boolean,
    val catalogProductId: String?,
)

data class NewShoppingItem(
    val listId: String,
    val name: String,
    val quantity: Double = 1.0,
    val unit: String? = null,
    val catalogProductId: String? = null,
)
