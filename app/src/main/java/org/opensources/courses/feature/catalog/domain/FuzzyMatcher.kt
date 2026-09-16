package org.opensources.courses.feature.catalog.domain

/**
 * Typo-tolerant matching: every query word must be close to the beginning of a word of the text.
 *
 * Closeness is the optimal-string-alignment distance (insertions, deletions, substitutions and
 * adjacent transpositions) between the query word and the best prefix of the text word. The
 * tolerance grows with the word length so short inputs stay strict: `lati` finds `lait`,
 * `tomatte` finds `tomates`, but `riz` does not find `rôti`.
 */
class FuzzyMatcher {
    /** [textWords] are the words of a normalized text ([TextNormalizer.words]). */
    fun matches(
        query: SearchQuery,
        textWords: List<String>,
    ): Boolean {
        if (query.words.isEmpty() || query.text.length < MIN_QUERY_LENGTH) return false
        return query.words.all { queryWord ->
            val allowed = allowedDistance(queryWord.length)
            textWords.any { prefixDistance(queryWord, it, allowed) <= allowed }
        }
    }

    fun allowedDistance(length: Int): Int =
        when {
            length < MIN_QUERY_LENGTH -> 0
            length < LONG_WORD_LENGTH -> 1
            else -> 2
        }

    /** Minimum distance between [query] and any prefix of [word]; stops early above [limit]. */
    fun prefixDistance(
        query: String,
        word: String,
        limit: Int = Int.MAX_VALUE,
    ): Int {
        if (query.isEmpty()) return 0
        val columns = minOf(word.length, query.length + limit.coerceAtMost(query.length))
        // A transposition looks two rows back at most: three rows, swapped in turn, are enough.
        var beforePrevious = IntArray(columns + 1)
        var previous = IntArray(columns + 1) { it }
        var current = IntArray(columns + 1)
        for (i in 1..query.length) {
            current[0] = i
            var rowMinimum = i
            for (j in 1..columns) {
                val cost = if (query[i - 1] == word[j - 1]) 0 else 1
                var value = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                if (i > 1 && j > 1 && query[i - 1] == word[j - 2] && query[i - 2] == word[j - 1]) {
                    value = minOf(value, beforePrevious[j - 2] + 1)
                }
                current[j] = value
                rowMinimum = minOf(rowMinimum, value)
            }
            if (rowMinimum > limit) return rowMinimum
            val recycled = beforePrevious
            beforePrevious = previous
            previous = current
            current = recycled
        }
        return previous.min()
    }

    companion object {
        const val MIN_QUERY_LENGTH = 4
        private const val LONG_WORD_LENGTH = 7
    }
}
