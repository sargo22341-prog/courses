package org.opensources.courses.feature.shopping.domain

import org.opensources.courses.feature.catalog.domain.CategoryNameKeys
import org.opensources.courses.feature.catalog.domain.ProductSuggestion
import org.opensources.courses.feature.catalog.domain.TextNormalizer
import org.opensources.courses.feature.language.domain.AppLanguage

/**
 * The suggestion that is the typed name itself, case, accents, punctuation and plural aside: with a
 * quantity, names come in the plural ("2 pains"), and must still pick the catalog's "Pain" rather
 * than create a second product. The exact spelling wins over a plural form.
 */
object ExactSuggestionFinder {
    fun find(
        suggestions: List<ProductSuggestion>,
        name: String,
        language: AppLanguage,
    ): ProductSuggestion? {
        val normalized = TextNormalizer.normalize(name)
        if (normalized.isEmpty() || suggestions.isEmpty()) return null
        return CategoryNameKeys.of(normalized, language).firstNotNullOfOrNull { key -> suggestions.firstOrNull { it.normalizedName == key } }
    }
}
