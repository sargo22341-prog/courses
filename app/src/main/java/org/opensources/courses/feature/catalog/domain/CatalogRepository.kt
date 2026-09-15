package org.opensources.courses.feature.catalog.domain

import kotlinx.coroutines.flow.Flow

interface CatalogRepository {
    /** Products whose name or alias contains [normalizedQuery]. */
    suspend fun findCandidates(
        normalizedQuery: String,
        limit: Int,
    ): List<ProductCandidate>

    /** Wider, cheaper pre-selection for typo-tolerant matching (same initial letter). */
    suspend fun findFuzzyCandidates(
        normalizedQuery: String,
        limit: Int,
    ): List<ProductCandidate>

    /** Existing product with the same normalized name, or a new custom product. */
    suspend fun getOrCreateCustomProduct(name: String): CatalogProduct

    suspend fun recordUsage(productId: String)

    /** Replaces the OpenFoodFacts part of the catalog; usage statistics are preserved. */
    suspend fun replaceRemoteCatalog(
        version: String,
        products: List<CatalogImportProduct>,
    )

    fun observeProductCount(): Flow<Int>

    /**
     * Shop section of the catalog products named exactly like one of [normalizedNames], keyed by
     * that name. Names without a known section are absent; the bundled catalog wins over
     * OpenFoodFacts when both know a name. Updated when the catalog changes.
     */
    fun observeCategories(normalizedNames: Set<String>): Flow<Map<String, GroceryCategory>>
}
