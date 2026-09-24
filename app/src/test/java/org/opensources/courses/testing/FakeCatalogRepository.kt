package org.opensources.courses.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.opensources.courses.feature.catalog.domain.CatalogImportProduct
import org.opensources.courses.feature.catalog.domain.CatalogProduct
import org.opensources.courses.feature.catalog.domain.CatalogProductRef
import org.opensources.courses.feature.catalog.domain.CatalogRepository
import org.opensources.courses.feature.catalog.domain.CatalogSource
import org.opensources.courses.feature.catalog.domain.GroceryCategory
import org.opensources.courses.feature.catalog.domain.ProductCandidate
import org.opensources.courses.feature.catalog.domain.ProductSuggestion
import org.opensources.courses.feature.catalog.domain.TextNormalizer

fun product(
    name: String,
    useCount: Int = 0,
    lastUsedAt: Long? = null,
    baseScore: Int = 0,
    aliases: List<String> = emptyList(),
    category: String? = null,
    id: String = "id:$name",
    source: CatalogSource = CatalogSource.SEED,
): ProductCandidate =
    ProductCandidate(
        product = CatalogProduct(id, name, category, source),
        normalizedName = TextNormalizer.normalize(name),
        normalizedAliases = aliases.map(TextNormalizer::normalize),
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

    /** Version written by the last import of each source, and the products it wrote. */
    val importedVersions = mutableMapOf<CatalogSource, String>()
    val imported = mutableMapOf<CatalogSource, List<CatalogImportProduct>>()

    /** Shop sections of catalog products, by normalized name. */
    val categories = mutableMapOf<String, GroceryCategory>()

    /** Shop sections of catalog products, by id. */
    val categoriesById = mutableMapOf<String, GroceryCategory>()
    private val count = MutableStateFlow(initial.size)
    private val usageVersion = MutableStateFlow(0)

    override suspend fun findCandidates(
        normalizedQuery: String,
        limit: Int,
    ): List<ProductCandidate> =
        candidates
            .filter { candidate ->
                (listOf(candidate.normalizedName) + candidate.normalizedAliases).any { it.contains(normalizedQuery) }
            }.take(limit)

    override suspend fun findFuzzyCandidates(
        normalizedQuery: String,
        limit: Int,
    ): List<ProductCandidate> {
        val initial = normalizedQuery.take(1)
        return candidates
            .filter { candidate -> TextNormalizer.words(candidate.normalizedName).any { it.startsWith(initial) } }
            .take(limit)
    }

    override suspend fun getOrCreateCustomProduct(name: String): CatalogProduct {
        val normalized = TextNormalizer.normalize(name)
        candidates.firstOrNull { it.normalizedName == normalized }?.let { return it.product }
        val created = CatalogProduct("custom:$normalized", name, null, CatalogSource.CUSTOM)
        candidates += ProductCandidate(created, normalized, emptyList(), 0, 0, null)
        count.value = candidates.size
        return created
    }

    /** Products whose deletion was asked, in order. */
    val deletionRequests = mutableListOf<String>()

    /** The fake knows no list item: a custom product is deleted when it has no usage. */
    override suspend fun deleteUnusedCustomProduct(productId: String) {
        deletionRequests += productId
        if ((usage[productId] ?: 0) == 0) candidates.removeAll { it.product.id == productId && it.product.source == CatalogSource.CUSTOM }
        count.value = candidates.size
    }

    override suspend fun recordUsage(productId: String) {
        usage[productId] = (usage[productId] ?: 0) + 1
        usageVersion.value++
    }

    /** Most added first; equal counts keep the catalog order, the fake having no clock. */
    override fun observeFrequentProducts(limit: Int): Flow<List<ProductSuggestion>> =
        usageVersion.map {
            candidates
                .filter { (usage[it.product.id] ?: 0) > 0 }
                .sortedByDescending { usage[it.product.id] }
                .take(limit)
                .map { ProductSuggestion(it.product.id, it.product.name, it.product.category, it.normalizedName) }
        }

    override fun observeHasUsage(): Flow<Boolean> = usageVersion.map { usage.values.any { it > 0 } }

    override suspend fun clearUsage() {
        usage.clear()
        usageVersion.value++
    }

    override suspend fun replaceCatalog(
        source: CatalogSource,
        version: String,
        products: List<CatalogImportProduct>,
    ) {
        importedVersions[source] = version
        imported[source] = products
    }

    override fun observeProductCount(): Flow<Int> = count

    override suspend fun findByNormalizedNames(normalizedNames: Set<String>): List<CatalogProductRef> =
        refs().filter { it.normalizedName in normalizedNames }

    override suspend fun findByNormalizedAliases(normalizedAliases: Set<String>): List<CatalogProductRef> =
        candidates.flatMap { candidate ->
            candidate.normalizedAliases.filter { it in normalizedAliases }.map { CatalogProductRef(candidate.product.id, it, candidate.product.source) }
        }

    override suspend fun findByIds(ids: Set<String>): List<CatalogProductRef> = refs().filter { it.id in ids }

    /** Number of category queries opened, by name or by id. */
    var categoryQueries = 0
        private set

    override fun observeCategories(normalizedNames: Set<String>): Flow<Map<String, GroceryCategory>> {
        categoryQueries++
        return flowOf(categories.filterKeys { it in normalizedNames })
    }

    override fun observeCategoriesByIds(ids: Set<String>): Flow<Map<String, GroceryCategory>> {
        categoryQueries++
        return flowOf(categoriesById.filterKeys { it in ids })
    }

    private fun refs() = candidates.map { CatalogProductRef(it.product.id, it.normalizedName, it.product.source) }
}
