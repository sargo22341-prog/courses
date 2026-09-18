package org.opensources.courses.feature.shopping.presentation.components

import androidx.compose.runtime.Immutable
import org.opensources.courses.feature.shopping.domain.ItemSection
import org.opensources.courses.feature.shopping.domain.ShoppingItem

/**
 * One row of the shopping list, in display order: the list can then find the position of an item
 * (to scroll to it) from the same rows it shows.
 */
@Immutable
internal sealed interface ListRow {
    val key: String
    val contentType: String

    data object AllPurchased : ListRow {
        override val key = "all_purchased"
        override val contentType = "status"
    }

    data class CategoryTitle(
        val section: ItemSection,
    ) : ListRow {
        override val key = "category_${section.category.name}"
        override val contentType = "category_header"
    }

    data object PurchasedTitle : ListRow {
        override val key = "purchased_header"
        override val contentType = "purchased_header"
    }

    data class Item(
        val item: ShoppingItem,
    ) : ListRow {
        override val key = item.id
        override val contentType = "item_row"
    }
}

internal fun listRows(
    toBuy: List<ShoppingItem>,
    toBuySections: List<ItemSection>?,
    purchased: List<ShoppingItem>,
    hidePurchased: Boolean,
): List<ListRow> =
    buildList {
        if (toBuy.isEmpty()) add(ListRow.AllPurchased)
        if (toBuySections == null) {
            toBuy.mapTo(this, ListRow::Item)
        } else {
            toBuySections.forEach { section ->
                add(ListRow.CategoryTitle(section))
                section.items.mapTo(this, ListRow::Item)
            }
        }
        if (purchased.isNotEmpty() && !hidePurchased) {
            add(ListRow.PurchasedTitle)
            purchased.mapTo(this, ListRow::Item)
        }
    }
