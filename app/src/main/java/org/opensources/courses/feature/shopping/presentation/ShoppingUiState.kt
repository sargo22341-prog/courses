package org.opensources.courses.feature.shopping.presentation

import org.opensources.courses.core.sync.SyncSnapshot
import org.opensources.courses.feature.catalog.domain.ProductSuggestion
import org.opensources.courses.feature.catalog.domain.TextNormalizer
import org.opensources.courses.feature.shopping.domain.ItemEntry
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
    /** Found for [searchedEntry], the name typed without its quantity. */
    val suggestions: List<ProductSuggestion> = emptyList(),
    /** The text [suggestions] were searched for, split into name and quantity. */
    val searchedEntry: ItemEntry = ItemEntry(""),
    /** The suggestion that is exactly the name typed, plural aside: the one the keyboard "done" adds. */
    val exactSuggestion: ProductSuggestion? = null,
    /** Products added most often and not waiting in the list; empty when the history is turned off. */
    val history: List<ProductSuggestion> = emptyList(),
    val sync: SyncSnapshot = SyncSnapshot.Initial,
    /** A pull to refresh is running. */
    val isRefreshing: Boolean = false,
    /** Deleted but still undoable: already left out of [toBuy], [toBuySections] and [purchased]. */
    val pendingDeletion: ShoppingItem? = null,
    /** Just added: the list scrolls to it and lights it up once, then forgets it. */
    val highlightedItemId: String? = null,
) {
    /** "+ Ajouter …" is offered unless a suggestion is exactly what was typed. */
    val offersCustomItem: Boolean
        get() = exactSuggestion == null && TextNormalizer.normalize(searchedEntry.name).isNotEmpty()
}
