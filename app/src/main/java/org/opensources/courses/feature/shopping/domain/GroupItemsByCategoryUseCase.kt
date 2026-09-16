package org.opensources.courses.feature.shopping.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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

/** [sections] sort exactly [items]: both come from the same emission. */
data class CategorizedItems(
    val items: List<ShoppingItem>,
    val sections: List<ItemSection>,
)

/** The same sections with their checked items left out; a section left empty disappears. */
fun List<ItemSection>.withoutChecked(): List<ItemSection> =
    mapNotNull { section -> section.items.filterNot { it.isChecked }.takeIf { it.isNotEmpty() }?.let { section.copy(items = it) } }

/**
 * Sorts items into shop sections. The section is first the one of the catalog product carrying the
 * item's name in the app language (singular or plural, see [CategoryNameKeys]): items typed by hand,
 * renamed, or written in Home Assistant are placed the same way as long as OpenFoodFacts or the
 * bundled catalog knows their name. Otherwise it is the section of the product the item is linked to
 * ([LinkItemsToCatalogUseCase]), which keeps items written before a language change in place.
 * Unknown items go to [GroceryCategory.OTHER]. Sections follow the order of [GroceryCategory]; items
 * keep their order.
 *
 * The catalog is queried again only when the names or links of the items change: checking an item,
 * the most frequent change while shopping, sorts the items again without any query.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupItemsByCategoryUseCase
    @Inject
    constructor(
        private val catalog: CatalogRepository,
        private val languages: AppLanguageRepository,
    ) {
        operator fun invoke(items: Flow<List<ShoppingItem>>): Flow<CategorizedItems> =
            languages.language.flatMapLatest { language -> group(items, language) }

        private fun group(
            items: Flow<List<ShoppingItem>>,
            language: AppLanguage,
        ): Flow<CategorizedItems> =
            channelFlow {
                // Collected once and shared by the two uses below.
                val latest = MutableStateFlow<KeyedItems?>(null)
                launch { items.collect { latest.value = KeyedItems(it, language) } }
                val current = latest.filterNotNull()
                current.map { it.lookup }.distinctUntilChanged().collectLatest { lookup ->
                    combine(current, catalog.observeCategories(lookup.names), catalog.observeCategoriesByIds(lookup.productIds)) { keyed, byName, byLink ->
                        // Items whose names are not looked up yet wait for the query that follows.
                        keyed.takeIf { it.lookup == lookup }?.categorized(byName, byLink)
                    }.filterNotNull().collect { send(it) }
                }
            }

        /** What the catalog is asked about a list of items. */
        private data class CategoryLookup(
            val names: Set<String>,
            val productIds: Set<String>,
        )

        private class KeyedItems(
            val items: List<ShoppingItem>,
            language: AppLanguage,
        ) {
            private val keysByItemId = items.associate { it.id to CategoryNameKeys.of(TextNormalizer.normalize(it.name), language) }
            val lookup = CategoryLookup(keysByItemId.values.flatten().toSet(), items.mapNotNull { it.catalogProductId }.toSet())

            fun categorized(
                byName: Map<String, GroceryCategory>,
                byLink: Map<String, GroceryCategory>,
            ): CategorizedItems {
                val sections =
                    items
                        .groupBy { item ->
                            keysByItemId.getValue(item.id).firstNotNullOfOrNull(byName::get)
                                ?: item.catalogProductId?.let(byLink::get)
                                ?: GroceryCategory.OTHER
                        }.toSortedMap()
                        .map { (category, sectionItems) -> ItemSection(category, sectionItems) }
                return CategorizedItems(items, sections)
            }
        }
    }
