package org.opensources.courses.feature.catalog.domain

/**
 * Parts of a free text that may name a catalog product, most likely first: recipe texts wrap the
 * food in other words ("gousse ail", "graines de sésame ou selon le goût", "oignon rouge émincé").
 * Every run of consecutive words is a candidate, the longest first, then the earliest: "oignon rouge"
 * is tried before "oignon", "oignon" before "rouge". A run never starts nor ends with a linking word
 * ("de", "ou", "selon"…), never is a lone number, and a single word needs [MIN_WORD_LENGTH] letters.
 */
object ProductNameWindows {
    /** [normalizedText] is a [TextNormalizer] form. */
    fun of(normalizedText: String): List<String> {
        val words = TextNormalizer.words(normalizedText)
        val windows = mutableListOf<String>()
        for (size in minOf(words.size, MAX_WORDS) downTo 1) {
            for (start in 0..words.size - size) {
                val window = words.subList(start, start + size)
                if (isCandidate(window)) windows += window.joinToString(" ")
            }
        }
        return windows.distinct()
    }

    private fun isCandidate(window: List<String>): Boolean =
        window.first() !in LINKING_WORDS &&
            window.last() !in LINKING_WORDS &&
            window.none { word -> word.all(Char::isDigit) } &&
            (window.size > 1 || window.single().length >= MIN_WORD_LENGTH)

    /** Longer runs are notes rather than product names ("à ajouter au dernier moment"). */
    private const val MAX_WORDS = 5
    private const val MIN_WORD_LENGTH = 3

    /** Articles, prepositions and conjunctions of the six languages of the app, normalized. */
    private val LINKING_WORDS =
        setOf(
            // French
            "de", "d", "du", "des", "le", "la", "les", "l", "un", "une", "et", "ou", "a", "au", "aux", "en", "pour", "avec", "sans",
            "selon", "gout", "sur", "par",
            // English
            "of", "the", "and", "or", "an", "to", "for", "with", "without", "taste", "in",
            // German
            "der", "die", "das", "dem", "den", "und", "oder", "mit", "ohne", "von", "vom", "zum", "zur", "ein", "eine", "nach",
            // Spanish
            "el", "los", "las", "y", "o", "con", "sin", "del", "al", "para", "unos", "unas", "gusto",
            // Italian
            "il", "lo", "i", "gli", "e", "di", "della", "dello", "degli", "delle", "per", "piacere",
            // Portuguese
            "os", "as", "com", "sem", "do", "dos", "da", "das", "um", "uma", "gosto",
        )
}
