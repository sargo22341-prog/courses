package org.opensources.courses.feature.language.domain

import java.util.Locale

/**
 * Languages of the interface and of the food catalog. [tag] is the ISO 639-1 code shared by Android
 * resources (`values-de`), the per-app language setting and the OpenFoodFacts taxonomy (`de`).
 * Declared in the order of their own names (Deutsch, English, Español…), the order they are offered in.
 */
enum class AppLanguage(
    val tag: String,
) {
    GERMAN("de"),
    ENGLISH("en"),
    SPANISH("es"),
    FRENCH("fr"),
    ITALIAN("it"),
    PORTUGUESE("pt"),
    ;

    /** Number formats of the language (decimal separator of a quantity). */
    val locale: Locale get() = Locale.forLanguageTag(tag)

    companion object {
        /** Language of the unqualified `values` resources, shown when no preferred language is supported. */
        val Fallback = ENGLISH

        /**
         * The first supported language among [languageTags] (BCP 47, most preferred first), as Android
         * picks resources: `pt-BR` gives Portuguese, `ja-JP, de-DE` gives German; [Fallback] otherwise.
         */
        fun resolve(languageTags: List<String>): AppLanguage = languageTags.firstNotNullOfOrNull(::fromTag) ?: Fallback

        fun fromTag(languageTag: String): AppLanguage? {
            val language = languageTag.trim().substringBefore('-').substringBefore('_').lowercase(Locale.ROOT)
            return entries.firstOrNull { it.tag == language }
        }
    }
}
