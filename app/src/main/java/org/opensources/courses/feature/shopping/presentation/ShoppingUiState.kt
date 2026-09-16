package org.opensources.courses.feature.shopping.presentation

import org.opensources.courses.core.sync.SyncSnapshot
import org.opensources.courses.feature.catalog.domain.ProductSuggestion
import org.opensources.courses.feature.catalog.domain.TextNormalizer
import org.opensources.courses.feature.shopping.domain.ItemSection
import org.opensources.courses.feature.shopping.domain.ShoppingItem

data class ShoppingUiState(
    val isLoading: Boolean = true,
    val listName: String = "",
    val toBuy: List<ShoppingItem> = emptyList(),
    /** [toBuy] grouped by shop section; null when the "Ranger par catégorie" setting is off. */
    val toBuySections: List<ItemSection>? = null,
    val purchased: List<ShoppingItem> = emptyList(),
    val hidePurchased: Boolean = false,
    val suggestions: List<ProductSuggestion> = emptyList(),
    val sync: SyncSnapshot = SyncSnapshot.Initial,
    /** A pull to refresh is running. */
    val isRefreshing: Boolean = false,
    /** Deleted but still undoable: already left out of [toBuy], [toBuySections] and [purchased]. */
    val pendingDeletion: ShoppingItem? = null,
) {
    /** "+ Ajouter …" is offered unless a suggestion is exactly what was typed. */
    fun offersCustomItem(query: String): Boolean {
        val normalized = TextNormalizer.normalize(query)
        return normalized.isNotEmpty() && suggestions.none { TextNormalizer.normalize(it.name) == normalized }
    }
}
