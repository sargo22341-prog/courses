package org.opensources.courses.feature.catalog.domain

import java.text.Normalizer
import java.util.Locale

/**
 * Canonical form used for every search comparison, in every language: lower case, no accents,
 * ligatures and ß expanded, punctuation turned into single spaces.
 * `Œufs d'Élevage` → `oeufs d elevage`, `Weißbrot` → `weissbrot`.
 */
object TextNormalizer {
    private val DIACRITICS = Regex("\\p{Mn}+")
    private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")

    fun normalize(text: String): String {
        val lower =
            text
                .lowercase(Locale.ROOT)
                .replace("œ", "oe")
                .replace("æ", "ae")
                .replace("ß", "ss")
        val withoutAccents = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(DIACRITICS, "")
        return withoutAccents.replace(NON_ALPHANUMERIC, " ").trim()
    }

    /** Words of an already normalized text. */
    fun words(normalized: String): List<String> = normalized.split(' ').filter { it.isNotEmpty() }
}
