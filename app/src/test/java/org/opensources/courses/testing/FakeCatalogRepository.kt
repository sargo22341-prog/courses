package org.opensources.courses.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.opensources.courses.feature.catalog.domain.CatalogImportProduct
import org.opensources.courses.feature.catalog.domain.CatalogProduct
import org.opensources.courses.feature.catalog.domain.CatalogRepository
import org.opensources.courses.feature.catalog.domain.CatalogSource
import org.opensources.courses.feature.catalog.domain.GroceryCategory
import org.opensources.courses.feature.catalog.domain.ProductCandidate
import org.opensources.courses.feature.catalog.domain.TextNormalizer

fun product(
    name: String,
    useCount: Int = 0,
    lastUsedAt: Long? = null,
    baseScore: Int = 0,
    aliases: List<String> = emptyList(),
    category: String? = null,
): ProductCandidate =
    ProductCandidate(
        product = CatalogProduct("id:$name", name, category, null, null, CatalogSource.SEED),
        aliases = aliases,
        baseScore = baseScore,
        useCount = useCount,
        lastUsedAt = lastUsedAt,
    )

/** Mimics the SQL pre-selection of the Room implementation (substring and initial letter). */
class FakeCatalogRepository(
    initial: List<ProductCandidate> = emptyList(),
) : CatalogRepository {
    val candidates = initial.toMutableList()
    val usage = mutableMapOf<String, Int>()
    var importedVersion: String? = null
    var imported: List<CatalogImportProduct> = emptyList()

    /** Shop sections of catalog products, by normalized name. */
    val categories = mutableMapOf<String, GroceryCategory>()
    private val count = MutableStateFlow(initial.size)

    override suspend fun findCandidates(
        normalizedQuery: String,
        limit: Int,
    ): List<ProductCandidate> =
        candidates
            .filter { candidate ->
                (listOf(candidate.product.name) + candidate.aliases).any { TextNormalizer.normalize(it).contains(normalizedQuery) }
            }.take(limit)

    override suspend fun findFuzzyCandidates(
        normalizedQuery: String,
        limit: Int,
    ): List<ProductCandidate> {
        val initial = normalizedQuery.take(1)
        return candidates
            .filter { candidate -> TextNormalizer.words(TextNormalizer.normalize(candidate.product.name)).any { it.startsWith(initial) } }
            .take(limit)
    }

    override suspend fun getOrCreateCustomProduct(name: String): CatalogProduct {
        val normalized = TextNormalizer.normalize(name)
        candidates.firstOrNull { TextNormalizer.normalize(it.product.name) == normalized }?.let { return it.product }
        val created = CatalogProduct("custom:$normalized", name, null, null, null, CatalogSource.CUSTOM)
        candidates += ProductCandidate(created, emptyList(), 0, 0, null)
        count.value = candidates.size
        return created
    }

    override suspend fun recordUsage(productId: String) {
        usage[productId] = (usage[productId] ?: 0) + 1
    }

    override suspend fun replaceRemoteCatalog(
        version: String,
        products: List<CatalogImportProduct>,
    ) {
        importedVersion = version
        imported = products
    }

    override fun observeProductCount(): Flow<Int> = count

    override fun observeCategories(normalizedNames: Set<String>): Flow<Map<String, GroceryCategory>> =
        flowOf(categories.filterKeys { it in normalizedNames })
}
