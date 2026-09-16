package org.opensources.courses.feature.catalog.domain

/** How a normalized text matches a normalized query, from best to worst. */
enum class MatchKind(
    val weight: Int,
) {
    EXACT(4_000),
    PREFIX(3_000),
    WORD_PREFIX(2_000),
    CONTAINS(1_000),
    FUZZY(0),
}

/** A candidate that matches the query, with its [score]. */
class ScoredCandidate(
    val candidate: ProductCandidate,
    val score: Int,
)

/**
 * Orders catalog candidates for the autocomplete.
 *
 * The match kind dominates (exact > prefix > word prefix > partial > fuzzy): each tier is 1 000
 * points apart and the sum of all bonuses stays below that, so a habit never pushes a worse match
 * above a better one. Inside a tier, the user's habits decide:
 * - usage: +20 per addition, capped at 20 additions;
 * - recency: +150 when used in the last 3 days, +100 within 14 days, +40 within 60 days;
 * - catalog base score (curated products first, generic before specific): +10 per point.
 * Ties are broken by shorter name, then alphabetically, which keeps results deterministic.
 *
 * Candidates carry their names already normalized: the ranking runs at each keystroke over up to
 * thousands of them, and never normalizes a text itself.
 */
class SuggestionRanker(
    private val fuzzyMatcher: FuzzyMatcher = FuzzyMatcher(),
) {
    fun matchKind(
        query: SearchQuery,
        normalizedText: String,
    ): MatchKind? {
        if (query.text.isEmpty() || normalizedText.isEmpty()) return null
        if (normalizedText == query.text) return MatchKind.EXACT
        if (normalizedText.startsWith(query.text)) return MatchKind.PREFIX
        val textWords = TextNormalizer.words(normalizedText)
        return when {
            isWordPrefix(query, textWords) -> MatchKind.WORD_PREFIX
            normalizedText.contains(query.text) -> MatchKind.CONTAINS
            fuzzyMatcher.matches(query, textWords) -> MatchKind.FUZZY
            else -> null
        }
    }

    /** Score of [candidate] for [query], or null when it does not match at all. */
    fun score(
        query: SearchQuery,
        candidate: ProductCandidate,
        nowMillis: Long,
    ): Int? {
        val nameMatch = matchKind(query, candidate.normalizedName)
        val aliasMatch =
            candidate.normalizedAliases
                .mapNotNull { matchKind(query, it) }
                .minByOrNull { it.ordinal }
        val best = listOfNotNull(nameMatch, aliasMatch).minByOrNull { it.ordinal } ?: return null
        val aliasOnlyPenalty = if (nameMatch == null || nameMatch.ordinal > best.ordinal) ALIAS_PENALTY else 0
        return best.weight - aliasOnlyPenalty + usageBonus(candidate.useCount) +
            recencyBonus(candidate.lastUsedAt, nowMillis) + candidate.baseScore * BASE_SCORE_WEIGHT
    }

    /** The candidates that match [query], each with its score. */
    fun scored(
        query: SearchQuery,
        candidates: List<ProductCandidate>,
        nowMillis: Long,
    ): List<ScoredCandidate> = candidates.mapNotNull { candidate -> score(query, candidate, nowMillis)?.let { ScoredCandidate(candidate, it) } }

    /** The [limit] best of [scored], one per normalized name. */
    fun best(
        scored: List<ScoredCandidate>,
        limit: Int,
    ): List<ProductCandidate> =
        scored
            .sortedWith(RANKING_ORDER)
            .distinctBy { it.candidate.normalizedName }
            .take(limit)
            .map { it.candidate }

    fun rank(
        query: SearchQuery,
        candidates: List<ProductCandidate>,
        nowMillis: Long,
        limit: Int,
    ): List<ProductCandidate> = best(scored(query, candidates, nowMillis), limit)

    private fun isWordPrefix(
        query: SearchQuery,
        textWords: List<String>,
    ): Boolean = query.words.all { queryWord -> textWords.any { it.startsWith(queryWord) } }

    private fun usageBonus(useCount: Int): Int = useCount.coerceIn(0, MAX_COUNTED_USES) * USAGE_WEIGHT

    private fun recencyBonus(
        lastUsedAt: Long?,
        nowMillis: Long,
    ): Int {
        if (lastUsedAt == null) return 0
        val ageDays = (nowMillis - lastUsedAt).coerceAtLeast(0) / MILLIS_PER_DAY
        return when {
            ageDays <= 3 -> 150
            ageDays <= 14 -> 100
            ageDays <= 60 -> 40
            else -> 0
        }
    }

    private companion object {
        const val USAGE_WEIGHT = 20
        const val MAX_COUNTED_USES = 20
        const val BASE_SCORE_WEIGHT = 10
        const val ALIAS_PENALTY = 50
        const val MILLIS_PER_DAY = 86_400_000L

        // Compares without allocating: sorting thousands of candidates must not lower-case their names.
        val RANKING_ORDER =
            compareByDescending<ScoredCandidate> { it.score }
                .thenBy { it.candidate.product.name.length }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.candidate.product.name }
    }
}
