package org.opensources.courses.feature.catalog.domain

import org.opensources.courses.feature.language.domain.AppLanguage

/**
 * Catalog names under which a product may be filed, most faithful first. Lists say "Tomate" where
 * the OpenFoodFacts taxonomy says "Tomates" and "Tomates cerise", so after the exact name the
 * singular form, the plural forms of the first word and the plural of every word are tried, with
 * the endings of [AppLanguage] ([WordForms]).
 */
object CategoryNameKeys {
    fun of(
        normalizedName: String,
        language: AppLanguage,
    ): List<String> {
        val words = TextNormalizer.words(normalizedName)
        if (words.isEmpty()) return emptyList()
        val forms = WordForms.of(language)
        val singular = words.map(forms::singular)
        val firstWordPlurals = forms.plurals(singular.first()).map { listOf(it) + singular.drop(1) }
        val everyWordPlural = singular.map { forms.plurals(it).first() }
        return (listOf(words, singular) + firstWordPlurals + listOf(everyWordPlural)).map { it.joinToString(" ") }.distinct()
    }
}
