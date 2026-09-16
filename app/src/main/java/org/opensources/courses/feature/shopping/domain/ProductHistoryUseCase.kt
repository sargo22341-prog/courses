package org.opensources.courses.feature.shopping.domain

import kotlinx.coroutines.flow.Flow
import org.opensources.courses.feature.catalog.domain.CatalogRepository
import org.opensources.courses.feature.catalog.domain.ProductSuggestion
import org.opensources.courses.feature.catalog.domain.TextNormalizer
import javax.inject.Inject

/**
 * The products added most often, offered while nothing is typed in the search field. Local only:
 * the history is read from the usage statistics and never leaves the device.
 */
class ProductHistoryUseCase
    @Inject
    constructor(
        private val catalog: CatalogRepository,
    ) {
        /** More products than shown: those already waiting in the list are removed afterwards. */
        fun observeFrequent(): Flow<List<ProductSuggestion>> = catalog.observeFrequentProducts(CANDIDATE_LIMIT)

        /**
         * [frequent] without the products already waiting in the list ([toBuy]), recognised by their
         * catalog product or by their name: an item added in Home Assistant may not be linked yet.
         * Bought items stay offered: adding one again puts it back to "to buy".
         */
        fun notInList(
            frequent: List<ProductSuggestion>,
            toBuy: List<ShoppingItem>,
            limit: Int = SHOWN_LIMIT,
        ): List<ProductSuggestion> {
            if (frequent.isEmpty()) return emptyList()
            val listedIds = toBuy.mapNotNullTo(HashSet()) { it.catalogProductId }
            val listedNames = toBuy.mapTo(HashSet()) { TextNormalizer.normalize(it.name) }
            return frequent
                .asSequence()
                .filterNot { it.productId in listedIds || it.normalizedName in listedNames }
                // Several catalog sources may know the same name: the list would show it twice.
                .distinctBy { it.normalizedName }
                .take(limit)
                .toList()
        }

        private companion object {
            const val SHOWN_LIMIT = 30
            const val CANDIDATE_LIMIT = 100
        }
    }
