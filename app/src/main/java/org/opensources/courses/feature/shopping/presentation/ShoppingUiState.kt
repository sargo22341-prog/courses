package org.opensources.courses.feature.shopping.presentation

import org.opensources.courses.core.sync.SyncSnapshot
import org.opensources.courses.feature.catalog.domain.ProductSuggestion
import org.opensources.courses.feature.catalog.domain.TextNormalizer
import org.opensources.courses.feature.shopping.domain.ShoppingItem

data class ShoppingUiState(
    val isLoading: Boolean = true,
    val listName: String = "",
    val toBuy: List<ShoppingItem> = emptyList(),
    val purchased: List<ShoppingItem> = emptyList(),
    val hidePurchased: Boolean = false,
    val suggestions: List<ProductSuggestion> = emptyList(),
    val sync: SyncSnapshot = SyncSnapshot.Initial,
) {
    /** "+ Ajouter …" is offered unless a suggestion is exactly what was typed. */
    fun offersCustomItem(query: String): Boolean {
        val normalized = TextNormalizer.normalize(query)
        return normalized.isNotEmpty() && suggestions.none { TextNormalizer.normalize(it.name) == normalized }
    }
}
