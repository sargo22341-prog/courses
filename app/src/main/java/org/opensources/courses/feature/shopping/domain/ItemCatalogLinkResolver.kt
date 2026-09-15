package org.opensources.courses.feature.shopping.domain

import org.opensources.courses.feature.catalog.domain.CatalogProductRef
import org.opensources.courses.feature.catalog.domain.CatalogSource

/**
 * Which catalog product an item stands for, given the products carrying its name ([nameMatches],
 * most faithful name first) and the product it is linked to now ([current], null when it has none or
 * the product no longer exists):
 * 1. a bundled or OpenFoodFacts product named like the item;
 * 2. otherwise its current link when it is such a product: an item written in the previous language
 *    ("Lait" once the app is in English) keeps its product, whose name and section follow the new
 *    language since ids do not depend on the language;
 * 3. otherwise a custom product named like the item, then its current custom link.
 * Null when nothing fits: a custom product is then created, as when an item is typed.
 */
object ItemCatalogLinkResolver {
    fun resolve(
        nameMatches: List<CatalogProductRef>,
        current: CatalogProductRef?,
    ): String? =
        nameMatches.firstOrNull { it.source != CatalogSource.CUSTOM }?.id
            ?: current?.takeIf { it.source != CatalogSource.CUSTOM }?.id
            ?: nameMatches.firstOrNull()?.id
            ?: current?.id
}
