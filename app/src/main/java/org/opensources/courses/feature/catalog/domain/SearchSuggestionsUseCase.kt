package org.opensources.courses.feature.catalog.domain

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.opensources.courses.core.common.DefaultDispatcher
import java.time.Clock
import javax.inject.Inject

/**
 * Local-only autocomplete: never touches the network.
 *
 * Ranking compares up to thousands of candidates at each keystroke, so it runs on [DefaultDispatcher],
 * never on the main thread. Typo-tolerant candidates are only fetched when the direct matches do not
 * fill the suggestions.
 */
class SearchSuggestionsUseCase
    @Inject
    constructor(
        private val catalog: CatalogRepository,
        private val clock: Clock,
        @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) {
        private val ranker = SuggestionRanker()

        suspend operator fun invoke(
            query: String,
            limit: Int = DEFAULT_LIMIT,
        ): List<ProductSuggestion> =
            withContext(defaultDispatcher) {
                val searched = SearchQuery(TextNormalizer.normalize(query))
                if (searched.text.isEmpty()) return@withContext emptyList()
                val now = clock.millis()
                val direct = ranker.scored(searched, catalog.findCandidates(searched.text, CANDIDATE_LIMIT), now)
                var ranked = ranker.best(direct, limit)
                if (ranked.size < limit && searched.text.length >= FuzzyMatcher.MIN_QUERY_LENGTH) {
                    // A newer keystroke cancels this search: the typo-tolerant pass is then not worth starting.
                    ensureActive()
                    val found = direct.mapTo(HashSet()) { it.candidate.product.id }
                    val fuzzy = catalog.findFuzzyCandidates(searched.text, FUZZY_CANDIDATE_LIMIT).filterNot { it.product.id in found }
                    ranked = ranker.best(direct + ranker.scored(searched, fuzzy, now), limit)
                }
                ranked.map { ProductSuggestion(it.product.id, it.product.name, it.product.category, it.normalizedName) }
            }

        private companion object {
            const val DEFAULT_LIMIT = 8
            const val CANDIDATE_LIMIT = 300

            // Names are pre-selected by initial letter: in French, about 2 400 of the 6 800 products
            // share the most common one ("d", through "de"/"du"). A lower limit would drop valid matches.
            const val FUZZY_CANDIDATE_LIMIT = 3_000
        }
    }
