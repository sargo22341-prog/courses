package org.opensources.courses.feature.catalog.domain

import java.time.Clock
import javax.inject.Inject

/** Local-only autocomplete: never touches the network. */
class SearchSuggestionsUseCase
    @Inject
    constructor(
        private val catalog: CatalogRepository,
        private val clock: Clock,
    ) {
        private val ranker = SuggestionRanker()

        suspend operator fun invoke(
            query: String,
            limit: Int = DEFAULT_LIMIT,
        ): List<ProductSuggestion> {
            val normalized = TextNormalizer.normalize(query)
            if (normalized.isEmpty()) return emptyList()
            val now = clock.millis()
            var ranked = ranker.rank(normalized, catalog.findCandidates(normalized, CANDIDATE_LIMIT), now, limit)
            if (ranked.size < limit && normalized.length >= FuzzyMatcher.MIN_QUERY_LENGTH) {
                val fuzzy = catalog.findFuzzyCandidates(normalized, FUZZY_CANDIDATE_LIMIT)
                ranked = ranker.rank(normalized, (ranked + fuzzy).distinctBy { it.product.id }, now, limit)
            }
            return ranked.map { ProductSuggestion(it.product.id, it.product.name, it.product.category) }
        }

        private companion object {
            const val DEFAULT_LIMIT = 8
            const val CANDIDATE_LIMIT = 300
            const val FUZZY_CANDIDATE_LIMIT = 3_000
        }
    }
