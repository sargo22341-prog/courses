package org.opensources.courses.feature.shopping.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.opensources.courses.feature.catalog.domain.CatalogRepository
import org.opensources.courses.feature.catalog.domain.CategoryNameKeys
import org.opensources.courses.feature.catalog.domain.GroceryCategory
import org.opensources.courses.feature.catalog.domain.TextNormalizer
import javax.inject.Inject

data class ItemSection(
    val category: GroceryCategory,
    val items: List<ShoppingItem>,
)

/**
 * Sorts items into shop sections. The section is the one of the catalog product carrying the item's
 * name (singular or plural, see [CategoryNameKeys]), not of the product the item was created from:
 * items typed by hand, renamed, or written in Home Assistant are placed the same way as long as
 * OpenFoodFacts or the bundled catalog knows their name. Unknown names go to
 * [GroceryCategory.OTHER]. Sections follow the order of [GroceryCategory]; items keep their order.
 */
class GroupItemsByCategoryUseCase
    @Inject
    constructor(
        private val catalog: CatalogRepository,
    ) {
        operator fun invoke(items: List<ShoppingItem>): Flow<List<ItemSection>> {
            val keysByItemId = items.associate { it.id to CategoryNameKeys.of(TextNormalizer.normalize(it.name)) }
            return catalog.observeCategories(keysByItemId.values.flatten().toSet()).map { categories ->
                items
                    .groupBy { item -> keysByItemId.getValue(item.id).firstNotNullOfOrNull(categories::get) ?: GroceryCategory.OTHER }
                    .toSortedMap()
                    .map { (category, sectionItems) -> ItemSection(category, sectionItems) }
            }
        }
    }
