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
    /** Products added most often and not waiting in the list; empty when the history is turned off. */
    val history: List<ProductSuggestion> = emptyList(),
    val sync: SyncSnapshot = SyncSnapshot.Initial,
    /** A pull to refresh is running. */
    val isRefreshing: Boolean = false,
    /** Deleted but still undoable: already left out of [toBuy], [toBuySections] and [purchased]. */
    val pendingDeletion: ShoppingItem? = null,
) {
    /** The suggestion that is exactly [query], case, accents and punctuation aside. */
    fun exactSuggestion(query: String): ProductSuggestion? = suggestionNamed(TextNormalizer.normalize(query))

    /** "+ Ajouter …" is offered unless a suggestion is exactly what was typed. */
    fun offersCustomItem(query: String): Boolean {
        val normalized = TextNormalizer.normalize(query)
        return normalized.isNotEmpty() && suggestionNamed(normalized) == null
    }

    // Suggestions carry their normalized name: only the typed text is normalized here.
    private fun suggestionNamed(normalizedName: String): ProductSuggestion? =
        if (normalizedName.isEmpty()) null else suggestions.firstOrNull { it.normalizedName == normalizedName }
}
