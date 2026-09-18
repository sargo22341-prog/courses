package org.opensources.courses.feature.shopping.domain

import org.opensources.courses.feature.catalog.domain.CatalogRepository
import org.opensources.courses.feature.catalog.domain.TextNormalizer
import javax.inject.Inject

/**
 * Adds a product to a list the way a shopper expects:
 * - a product already waiting in the list gets its quantity incremented instead of a duplicate, unless
 *   it has a unit: "500 g" is a measure, not a count, so it is left as it is;
 * - a quantity typed with the name ([ItemEntry]) is added to the one waiting when both are in the
 *   same unit ("2 pain" on 1 pain makes 3, "500 g" on 500 g makes 1 000 g), and replaces it otherwise;
 * - a product already bought is put back to "to buy", with the quantity typed if any;
 * - free text becomes a custom catalog product, so it is suggested next time;
 * - every addition feeds the suggestion ranking (usage count and last use).
 */
class AddItemUseCase
    @Inject
    constructor(
        private val items: ShoppingItemRepository,
        private val catalog: CatalogRepository,
    ) {
        /** [quantity] and [unit]: typed with the name, or null to follow the usual rules. */
        suspend operator fun invoke(
            listId: String,
            name: String,
            catalogProductId: String? = null,
            quantity: Double? = null,
            unit: String? = null,
        ): ShoppingItem? {
            val cleanName = name.trim().replace(WHITESPACE, " ")
            if (cleanName.isEmpty()) return null
            val productId = catalogProductId ?: catalog.getOrCreateCustomProduct(cleanName).id
            val normalized = TextNormalizer.normalize(cleanName)
            val existing = items.getItems(listId).firstOrNull { TextNormalizer.normalize(it.name) == normalized }
            val result =
                when {
                    existing == null ->
                        items.addItem(
                            NewShoppingItem(listId = listId, name = cleanName, quantity = quantity ?: 1.0, unit = unit, catalogProductId = productId),
                        )
                    existing.isChecked -> {
                        items.setChecked(existing.id, checked = false)
                        if (quantity == null) existing.copy(isChecked = false) else update(existing.copy(isChecked = false), quantity, unit)
                    }
                    quantity != null ->
                        update(existing, if (sameUnit(existing.unit, unit)) existing.quantity + quantity else quantity, unit)
                    existing.unit != null -> existing
                    else -> update(existing, existing.quantity + 1, unit = null)
                }
            catalog.recordUsage(productId)
            return result
        }

        private suspend fun update(
            item: ShoppingItem,
            quantity: Double,
            unit: String?,
        ): ShoppingItem {
            items.updateItem(item.id, item.name, quantity, unit)
            return item.copy(quantity = quantity, unit = unit)
        }

        private fun sameUnit(
            first: String?,
            second: String?,
        ) = first?.trim()?.lowercase() == second?.trim()?.lowercase()

        private companion object {
            val WHITESPACE = Regex("\\s+")
        }
    }
