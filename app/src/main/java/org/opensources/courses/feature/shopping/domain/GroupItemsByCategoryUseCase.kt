package org.opensources.courses.feature.shopping.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import org.opensources.courses.feature.catalog.domain.CatalogRepository
import org.opensources.courses.feature.catalog.domain.CategoryNameKeys
import org.opensources.courses.feature.catalog.domain.GroceryCategory
import org.opensources.courses.feature.catalog.domain.TextNormalizer
import org.opensources.courses.feature.language.domain.AppLanguage
import org.opensources.courses.feature.language.domain.AppLanguageRepository
import javax.inject.Inject

data class ItemSection(
    val category: GroceryCategory,
    val items: List<ShoppingItem>,
)

/**
 * Sorts items into shop sections. The section is first the one of the catalog product carrying the
 * item's name in the app language (singular or plural, see [CategoryNameKeys]): items typed by hand,
 * renamed, or written in Home Assistant are placed the same way as long as OpenFoodFacts or the
 * bundled catalog knows their name. Otherwise it is the section of the product the item is linked to
 * ([LinkItemsToCatalogUseCase]), which keeps items written before a language change in place.
 * Unknown items go to [GroceryCategory.OTHER]. Sections follow the order of [GroceryCategory]; items
 * keep their order.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupItemsByCategoryUseCase
    @Inject
    constructor(
        private val catalog: CatalogRepository,
        private val languages: AppLanguageRepository,
    ) {
        operator fun invoke(items: List<ShoppingItem>): Flow<List<ItemSection>> = languages.language.flatMapLatest { group(items, it) }

        private fun group(
            items: List<ShoppingItem>,
            language: AppLanguage,
        ): Flow<List<ItemSection>> {
            val keysByItemId = items.associate { it.id to CategoryNameKeys.of(TextNormalizer.normalize(it.name), language) }
            val byName = catalog.observeCategories(keysByItemId.values.flatten().toSet())
            val byLink = catalog.observeCategoriesByIds(items.mapNotNull { it.catalogProductId }.toSet())
            return combine(byName, byLink) { nameCategories, linkCategories ->
                items
                    .groupBy { item ->
                        keysByItemId.getValue(item.id).firstNotNullOfOrNull(nameCategories::get)
                            ?: item.catalogProductId?.let(linkCategories::get)
                            ?: GroceryCategory.OTHER
                    }.toSortedMap()
                    .map { (category, sectionItems) -> ItemSection(category, sectionItems) }
            }
        }
    }
