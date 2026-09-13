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
    fun matches(
        normalizedQuery: String,
        normalizedText: String,
    ): Boolean {
        val queryWords = TextNormalizer.words(normalizedQuery)
        if (queryWords.isEmpty() || normalizedQuery.length < MIN_QUERY_LENGTH) return false
        val textWords = TextNormalizer.words(normalizedText)
        return queryWords.all { queryWord ->
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
        val rows = Array(query.length + 1) { IntArray(columns + 1) }
        for (j in 0..columns) rows[0][j] = j
        for (i in 1..query.length) {
            rows[i][0] = i
            var rowMinimum = i
            for (j in 1..columns) {
                val cost = if (query[i - 1] == word[j - 1]) 0 else 1
                var value = minOf(rows[i - 1][j] + 1, rows[i][j - 1] + 1, rows[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && query[i - 1] == word[j - 2] && query[i - 2] == word[j - 1]) {
                    value = minOf(value, rows[i - 2][j - 2] + 1)
                }
                rows[i][j] = value
                rowMinimum = minOf(rowMinimum, value)
            }
            if (rowMinimum > limit) return rowMinimum
        }
        return rows[query.length].min()
    }

    companion object {
        const val MIN_QUERY_LENGTH = 4
        private const val LONG_WORD_LENGTH = 7
    }
}
