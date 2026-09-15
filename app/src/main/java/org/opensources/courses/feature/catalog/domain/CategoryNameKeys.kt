package org.opensources.courses.feature.catalog.domain

/**
 * Catalog names under which a product may be filed, most faithful first. Lists say "Tomate" where
 * the OpenFoodFacts taxonomy says "Tomates" and "Tomates cerise", so after the exact name the
 * singular form, the plural of the first word and the plural of every word are tried.
 */
object CategoryNameKeys {
    fun of(normalizedName: String): List<String> {
        val words = TextNormalizer.words(normalizedName)
        if (words.isEmpty()) return emptyList()
        val singular = words.map(::singular)
        return listOf(
            words,
            singular,
            listOf(plural(singular.first())) + singular.drop(1),
            singular.map(::plural),
        ).map { it.joinToString(" ") }.distinct()
    }

    private fun singular(word: String): String = if (word.length > MIN_PLURAL_LENGTH && word.last() in PLURAL_ENDINGS) word.dropLast(1) else word

    private fun plural(word: String): String =
        when {
            word.last() in PLURAL_ENDINGS || word.last() == 'z' -> word
            word.endsWith("au") || word.endsWith("eu") -> word + "x"
            else -> word + "s"
        }

    /** Short words ("des", "jus", "riz") are never plural forms to strip. */
    private const val MIN_PLURAL_LENGTH = 3
    private val PLURAL_ENDINGS = setOf('s', 'x')
}
