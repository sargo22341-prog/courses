package org.opensources.courses.feature.catalog.data.remote

import org.opensources.courses.feature.catalog.domain.CatalogImportProduct
import org.opensources.courses.feature.catalog.domain.TextNormalizer

/**
 * Cleans the OpenFoodFacts categories taxonomy into shopping-list products.
 *
 * The raw file holds ~15 000 categories in many languages. Kept entries must:
 * - have a name in [language];
 * - not be a protected designation (AOP/IGP wines, cheeses…) or tied to an origin
 *   (`Miels du Jura`): too specific to be typed in a shopping list;
 * - be short (≤ 4 words, ≤ 40 characters) and contain no digit (`Laits 2ème âge`).
 * Names are de-duplicated on their normalized form, keeping the most generic entry.
 * The base score favours generic categories (close to a root) over specific ones.
 */
class TaxonomyCatalogMapper(
    private val language: String = "fr",
) {
    fun map(entries: Map<String, TaxonomyEntryDto>): List<CatalogImportProduct> {
        val byName = LinkedHashMap<String, CatalogImportProduct>()
        for ((id, entry) in entries) {
            val name = entry.name[language]?.trim() ?: continue
            if (!isUseful(entry, name)) continue
            val ancestors = ancestorsOf(id, entries)
            val product =
                CatalogImportProduct(
                    id = id,
                    name = name.replaceFirstChar { it.uppercaseChar() },
                    category = categoryName(ancestors, entries),
                    parentId = entry.parents.firstOrNull { entries[it]?.name?.containsKey(language) == true },
                    baseScore = (MAX_BASE_SCORE - ancestors.size).coerceAtLeast(0),
                )
            val key = TextNormalizer.normalize(product.name)
            val existing = byName[key]
            if (existing == null || existing.baseScore < product.baseScore) byName[key] = product
        }
        return byName.values.toList()
    }

    private fun isUseful(
        entry: TaxonomyEntryDto,
        name: String,
    ): Boolean =
        entry.protectedNameType == null &&
            entry.origins == null &&
            name.length <= MAX_NAME_LENGTH &&
            name.none { it.isDigit() } &&
            name.split(' ').count { it.isNotBlank() } <= MAX_WORDS

    /** Ancestors from the direct parent up to the root, following the first known parent. */
    private fun ancestorsOf(
        id: String,
        entries: Map<String, TaxonomyEntryDto>,
    ): List<String> {
        val chain = mutableListOf<String>()
        var current = entries[id]?.parents?.firstOrNull { it in entries }
        while (current != null && current != id && current !in chain && chain.size < MAX_DEPTH) {
            chain += current
            current = entries[current]?.parents?.firstOrNull { it in entries }
        }
        return chain
    }

    /** The ancestor just below the root reads like a shelf name (`Produits laitiers`, `Légumes`). */
    private fun categoryName(
        ancestors: List<String>,
        entries: Map<String, TaxonomyEntryDto>,
    ): String? {
        val shelf = if (ancestors.size >= 2) ancestors[ancestors.size - 2] else ancestors.lastOrNull()
        return shelf?.let { entries[it]?.name?.get(language) }
    }

    private companion object {
        const val MAX_WORDS = 4
        const val MAX_NAME_LENGTH = 40
        const val MAX_DEPTH = 15
        const val MAX_BASE_SCORE = 5
    }
}
