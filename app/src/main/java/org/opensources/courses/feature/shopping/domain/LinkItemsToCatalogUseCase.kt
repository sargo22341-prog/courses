package org.opensources.courses.feature.shopping.domain

import org.opensources.courses.feature.catalog.domain.CatalogRepository
import org.opensources.courses.feature.catalog.domain.CategoryNameKeys
import org.opensources.courses.feature.catalog.domain.TextNormalizer
import org.opensources.courses.feature.language.domain.AppLanguageRepository
import javax.inject.Inject

/**
 * Run after every catalog import, notably the one that follows a language change: each item of every
 * list is linked again to the product it stands for ([ItemCatalogLinkResolver]), found under its name
 * in the app language, singular or plural ([CategoryNameKeys]). A linked item gets the shop section
 * and the category of its product. Items are never renamed nor removed, and the link is local data:
 * nothing is sent to Home Assistant. Returns how many items got a new link.
 */
class LinkItemsToCatalogUseCase
    @Inject
    constructor(
        private val items: ShoppingItemRepository,
        private val catalog: CatalogRepository,
        private val languages: AppLanguageRepository,
    ) {
        suspend operator fun invoke(): Int {
            val all = items.getAllItems().filter { it.name.isNotBlank() }
            if (all.isEmpty()) return 0
            val language = languages.language.value
            val keysByItemId = all.associate { it.id to CategoryNameKeys.of(TextNormalizer.normalize(it.name), language) }
            val byName = catalog.findByNormalizedNames(keysByItemId.values.flatten().toSet()).groupBy { it.normalizedName }
            val linked = catalog.findByIds(all.mapNotNull { it.catalogProductId }.toSet()).associateBy { it.id }
            var relinked = 0
            for (item in all) {
                // Most faithful name first; for one name, the most reliable source first.
                val nameMatches = keysByItemId.getValue(item.id).flatMap { key -> byName[key].orEmpty().sortedBy { it.source.ordinal } }
                val productId =
                    ItemCatalogLinkResolver.resolve(nameMatches, item.catalogProductId?.let(linked::get))
                        ?: catalog.getOrCreateCustomProduct(item.name).id
                if (productId != item.catalogProductId && items.setCatalogProduct(item.id, item.name, productId)) relinked++
            }
            return relinked
        }
    }
