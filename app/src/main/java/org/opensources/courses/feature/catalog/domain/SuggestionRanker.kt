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
 */
class SuggestionRanker(
    private val fuzzyMatcher: FuzzyMatcher = FuzzyMatcher(),
) {
    fun matchKind(
        normalizedQuery: String,
        normalizedText: String,
    ): MatchKind? {
        if (normalizedQuery.isEmpty() || normalizedText.isEmpty()) return null
        return when {
            normalizedText == normalizedQuery -> MatchKind.EXACT
            normalizedText.startsWith(normalizedQuery) -> MatchKind.PREFIX
            isWordPrefix(normalizedQuery, normalizedText) -> MatchKind.WORD_PREFIX
            normalizedText.contains(normalizedQuery) -> MatchKind.CONTAINS
            fuzzyMatcher.matches(normalizedQuery, normalizedText) -> MatchKind.FUZZY
            else -> null
        }
    }

    /** Score of [candidate] for [normalizedQuery], or null when it does not match at all. */
    fun score(
        normalizedQuery: String,
        candidate: ProductCandidate,
        nowMillis: Long,
    ): Int? {
        val nameMatch = matchKind(normalizedQuery, TextNormalizer.normalize(candidate.product.name))
        val aliasMatch =
            candidate.aliases
                .mapNotNull { matchKind(normalizedQuery, TextNormalizer.normalize(it)) }
                .minByOrNull { it.ordinal }
        val best = listOfNotNull(nameMatch, aliasMatch).minByOrNull { it.ordinal } ?: return null
        val aliasOnlyPenalty = if (nameMatch == null || nameMatch.ordinal > best.ordinal) ALIAS_PENALTY else 0
        return best.weight - aliasOnlyPenalty + usageBonus(candidate.useCount) +
            recencyBonus(candidate.lastUsedAt, nowMillis) + candidate.baseScore * BASE_SCORE_WEIGHT
    }

    fun rank(
        normalizedQuery: String,
        candidates: List<ProductCandidate>,
        nowMillis: Long,
        limit: Int,
    ): List<ProductCandidate> =
        candidates
            .mapNotNull { candidate -> score(normalizedQuery, candidate, nowMillis)?.let { candidate to it } }
            .sortedWith(
                compareByDescending<Pair<ProductCandidate, Int>> { it.second }
                    .thenBy { it.first.product.name.length }
                    .thenBy { it.first.product.name.lowercase() },
            ).distinctBy { TextNormalizer.normalize(it.first.product.name) }
            .take(limit)
            .map { it.first }

    private fun isWordPrefix(
        query: String,
        text: String,
    ): Boolean {
        val textWords = TextNormalizer.words(text)
        return TextNormalizer.words(query).all { queryWord -> textWords.any { it.startsWith(queryWord) } }
    }

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
    }
}
