package org.opensources.courses.feature.catalog.domain

import org.opensources.courses.feature.language.domain.AppLanguage

/**
 * Singular and plural of a normalized word (lower case, no accents), from the regular endings of a
 * language only: irregular forms are simply not found. The forms are only looked up in the catalog,
 * after the exact name, so a wrong guess costs nothing.
 */
internal sealed interface WordForms {
    /** The most likely singular; the word itself when it does not look plural. */
    fun singular(word: String): String

    /** Plural candidates of a singular [word], most likely first; never empty. */
    fun plurals(word: String): List<String>

    companion object {
        fun of(language: AppLanguage): WordForms =
            when (language) {
                AppLanguage.FRENCH -> French
                AppLanguage.ENGLISH -> English
                AppLanguage.GERMAN -> German
                AppLanguage.SPANISH -> Spanish
                AppLanguage.ITALIAN -> Italian
                AppLanguage.PORTUGUESE -> Portuguese
            }

        /** Short words ("des", "jus", "riz", "ei") are never plural forms to strip. */
        private const val MIN_PLURAL_LENGTH = 3

        private fun String.canBePlural() = length > MIN_PLURAL_LENGTH
    }

    /** Tomates → tomate, gâteaux → gâteau. */
    data object French : WordForms {
        override fun singular(word: String) = if (word.canBePlural() && word.last() in "sx") word.dropLast(1) else word

        override fun plurals(word: String) =
            listOf(
                when {
                    word.last() in "sxz" -> word
                    word.endsWith("au") || word.endsWith("eu") -> word + "x"
                    else -> word + "s"
                },
            )
    }

    /** Berries → berry, tomatoes → tomato, peaches → peach, apples → apple. */
    data object English : WordForms {
        override fun singular(word: String) =
            when {
                !word.canBePlural() || word.endsWith("ss") -> word
                word.endsWith("ies") -> word.dropLast(3) + "y"
                listOf("oes", "ches", "shes", "sses", "xes", "zes").any(word::endsWith) -> word.dropLast(2)
                word.endsWith("s") -> word.dropLast(1)
                else -> word
            }

        override fun plurals(word: String) =
            when {
                word.endsWith("y") && word.length > 1 && word[word.length - 2] !in "aeiou" -> listOf(word.dropLast(1) + "ies")
                word.endsWith("ss") || listOf("x", "z", "ch", "sh").any(word::endsWith) -> listOf(word + "es")
                word.endsWith("s") -> listOf(word)
                word.endsWith("o") -> listOf(word + "es", word + "s")
                else -> listOf(word + "s")
            }
    }

    /** Tomaten → tomate, Brote → Brot, Eier → Ei (umlauts are already gone: Äpfel is "apfel"). */
    data object German : WordForms {
        override fun singular(word: String) =
            when {
                !word.canBePlural() -> word
                word.endsWith("innen") -> word.dropLast(3)
                word.endsWith("er") && word.length <= 5 -> word.dropLast(2)
                word.last() in "sne" -> word.dropLast(1)
                else -> word
            }

        override fun plurals(word: String) =
            when {
                word.endsWith("in") -> listOf(word + "nen")
                word.endsWith("e") || word.endsWith("el") || word.endsWith("er") -> listOf(word + "n", word)
                else -> listOf(word + "e", word + "en", word + "er", word + "s")
            }
    }

    /** Manzanas → manzana, limones → limón, nueces → nuez. */
    data object Spanish : WordForms {
        override fun singular(word: String) =
            when {
                !word.canBePlural() -> word
                word.endsWith("ces") -> word.dropLast(3) + "z"
                word.endsWith("es") && word[word.length - 3] in "lnrdjy" -> word.dropLast(2)
                word.endsWith("s") -> word.dropLast(1)
                else -> word
            }

        override fun plurals(word: String) =
            when {
                word.last() in "aeiou" -> listOf(word + "s")
                word.endsWith("z") -> listOf(word.dropLast(1) + "ces")
                word.last() in "sx" -> listOf(word)
                else -> listOf(word + "es")
            }
    }

    /** Mele → mela, pomodori → pomodoro, albicocche → albicocca. */
    data object Italian : WordForms {
        override fun singular(word: String) =
            when {
                !word.canBePlural() -> word
                word.endsWith("che") || word.endsWith("ghe") -> word.dropLast(2) + "a"
                word.endsWith("chi") || word.endsWith("ghi") -> word.dropLast(2) + "o"
                word.endsWith("i") -> word.dropLast(1) + "o"
                word.endsWith("e") -> word.dropLast(1) + "a"
                else -> word
            }

        override fun plurals(word: String) =
            when {
                word.endsWith("ca") || word.endsWith("ga") -> listOf(word.dropLast(1) + "he")
                word.endsWith("co") || word.endsWith("go") -> listOf(word.dropLast(1) + "hi", word.dropLast(1) + "i")
                word.endsWith("o") || word.endsWith("e") -> listOf(word.dropLast(1) + "i")
                word.endsWith("a") -> listOf(word.dropLast(1) + "e")
                // Foreign and truncated words do not change: yogurt, caffè.
                else -> listOf(word)
            }
    }

    /** Maçãs → maçã, limões → limão, pastéis → pastel, nozes → noz. */
    data object Portuguese : WordForms {
        override fun singular(word: String) =
            when {
                !word.canBePlural() -> word
                word.endsWith("oes") || word.endsWith("aes") -> word.dropLast(3) + "ao"
                word.endsWith("ns") -> word.dropLast(2) + "m"
                listOf("ais", "eis", "ois", "uis").any(word::endsWith) -> word.dropLast(2) + "l"
                word.endsWith("res") || word.endsWith("zes") -> word.dropLast(2)
                word.endsWith("s") -> word.dropLast(1)
                else -> word
            }

        override fun plurals(word: String) =
            when {
                word.endsWith("ao") -> listOf(word.dropLast(2) + "oes", word.dropLast(2) + "aes", word + "s")
                word.endsWith("m") -> listOf(word.dropLast(1) + "ns")
                listOf("al", "el", "ol", "ul").any(word::endsWith) -> listOf(word.dropLast(1) + "is")
                word.last() in "rz" -> listOf(word + "es")
                word.endsWith("s") -> listOf(word)
                else -> listOf(word + "s")
            }
    }
}
