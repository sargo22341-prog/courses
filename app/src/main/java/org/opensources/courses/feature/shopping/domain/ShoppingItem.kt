package org.opensources.courses.feature.shopping.domain

import org.opensources.courses.core.model.SyncStatus

data class ShoppingItem(
    val id: String,
    val listId: String,
    val name: String,
    val quantity: Double,
    val unit: String?,
    val isChecked: Boolean,
    val catalogProductId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: SyncStatus,
)

data class NewShoppingItem(
    val listId: String,
    val name: String,
    val quantity: Double = 1.0,
    val unit: String? = null,
    val catalogProductId: String? = null,
)
