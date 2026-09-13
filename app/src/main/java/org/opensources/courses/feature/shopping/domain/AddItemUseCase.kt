package org.opensources.courses.feature.shopping.domain

import org.opensources.courses.feature.catalog.domain.CatalogRepository
import org.opensources.courses.feature.catalog.domain.TextNormalizer
import javax.inject.Inject

/**
 * Adds a product to a list the way a shopper expects:
 * - a product already waiting in the list gets its quantity incremented instead of a duplicate;
 * - a product already bought is put back to "to buy";
 * - free text becomes a custom catalog product, so it is suggested next time;
 * - every addition feeds the suggestion ranking (usage count and last use).
 */
class AddItemUseCase
    @Inject
    constructor(
        private val items: ShoppingItemRepository,
        private val catalog: CatalogRepository,
    ) {
        suspend operator fun invoke(
            listId: String,
            name: String,
            catalogProductId: String? = null,
        ): ShoppingItem? {
            val cleanName = name.trim().replace(WHITESPACE, " ")
            if (cleanName.isEmpty()) return null
            val productId = catalogProductId ?: catalog.getOrCreateCustomProduct(cleanName).id
            val normalized = TextNormalizer.normalize(cleanName)
            val existing = items.getItems(listId).firstOrNull { TextNormalizer.normalize(it.name) == normalized }
            val result =
                when {
                    existing == null ->
                        items.addItem(NewShoppingItem(listId = listId, name = cleanName, catalogProductId = productId))
                    existing.isChecked -> {
                        items.setChecked(existing.id, checked = false)
                        existing.copy(isChecked = false)
                    }
                    else -> {
                        val quantity = existing.quantity + 1
                        items.updateItem(existing.id, existing.name, quantity, existing.unit)
                        existing.copy(quantity = quantity)
                    }
                }
            catalog.recordUsage(productId)
            return result
        }

        private companion object {
            val WHITESPACE = Regex("\\s+")
        }
    }
