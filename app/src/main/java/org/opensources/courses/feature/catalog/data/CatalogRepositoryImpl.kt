package org.opensources.courses.feature.catalog.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.opensources.courses.core.database.TransactionRunner
import org.opensources.courses.feature.catalog.data.local.CatalogAliasEntity
import org.opensources.courses.feature.catalog.data.local.CatalogDao
import org.opensources.courses.feature.catalog.data.local.CatalogProductEntity
import org.opensources.courses.feature.catalog.data.local.ProductCandidateRow
import org.opensources.courses.feature.catalog.data.local.toDomain
import org.opensources.courses.feature.catalog.domain.CatalogImportProduct
import org.opensources.courses.feature.catalog.domain.CatalogProduct
import org.opensources.courses.feature.catalog.domain.CatalogProductRef
import org.opensources.courses.feature.catalog.domain.CatalogRepository
import org.opensources.courses.feature.catalog.domain.CatalogSource
import org.opensources.courses.feature.catalog.domain.GroceryCategory
import org.opensources.courses.feature.catalog.domain.ProductCandidate
import org.opensources.courses.feature.catalog.domain.TextNormalizer
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

class CatalogRepositoryImpl
    @Inject
    constructor(
        private val dao: CatalogDao,
        private val transactions: TransactionRunner,
        private val clock: Clock,
    ) : CatalogRepository {
        // Normalized queries only contain [a-z0-9 ], so they never need LIKE escaping.
        override suspend fun findCandidates(
            normalizedQuery: String,
            limit: Int,
        ): List<ProductCandidate> = dao.search(normalizedQuery, limit).toCandidates()

        override suspend fun findFuzzyCandidates(
            normalizedQuery: String,
            limit: Int,
        ): List<ProductCandidate> = dao.fuzzyCandidates(normalizedQuery.take(1), limit).toCandidates()

        override suspend fun getOrCreateCustomProduct(name: String): CatalogProduct =
            transactions.inTransaction {
                val normalized = TextNormalizer.normalize(name)
                dao.findByNormalizedName(normalized)?.toDomain()
                    ?: CatalogProductEntity(
                        id = "$CUSTOM_PREFIX${UUID.randomUUID()}",
                        name = name,
                        normalizedName = normalized,
                        category = null,
                        brand = null,
                        parentId = null,
                        source = CatalogSource.CUSTOM,
                        baseScore = 0,
                        catalogVersion = null,
                    ).also { dao.insertProduct(it) }
                        .toDomain()
            }

        override suspend fun recordUsage(productId: String) = dao.recordUsage(productId, clock.millis())

        override suspend fun replaceRemoteCatalog(
            version: String,
            products: List<CatalogImportProduct>,
        ) = replaceSource(CatalogSource.OPEN_FOOD_FACTS, version, products)

        override suspend fun replaceSeedCatalog(
            version: String,
            products: List<CatalogImportProduct>,
        ) = replaceSource(CatalogSource.SEED, version, products)

        override fun observeProductCount(): Flow<Int> = dao.observeCount()

        override suspend fun findByNormalizedNames(normalizedNames: Set<String>): List<CatalogProductRef> =
            if (normalizedNames.isEmpty()) emptyList() else dao.findRefsByNormalizedNames(normalizedNames.toList()).map { it.toDomain() }

        override suspend fun findByIds(ids: Set<String>): List<CatalogProductRef> =
            if (ids.isEmpty()) emptyList() else dao.findRefsByIds(ids.toList()).map { it.toDomain() }

        override fun observeCategories(normalizedNames: Set<String>): Flow<Map<String, GroceryCategory>> {
            if (normalizedNames.isEmpty()) return flowOf(emptyMap())
            return dao.observeCategories(normalizedNames.toList()).map { rows ->
                rows
                    // The curated seed places products more reliably than the generic taxonomy.
                    .sortedBy { it.source.ordinal }
                    .distinctBy { it.normalizedName }
                    .associate { it.normalizedName to it.groceryCategory }
            }
        }

        override fun observeCategoriesByIds(ids: Set<String>): Flow<Map<String, GroceryCategory>> {
            if (ids.isEmpty()) return flowOf(emptyMap())
            return dao.observeCategoriesByIds(ids.toList()).map { rows -> rows.associate { it.id to it.groceryCategory } }
        }

        private suspend fun List<ProductCandidateRow>.toCandidates(): List<ProductCandidate> {
            if (isEmpty()) return emptyList()
            val aliases = dao.aliasesFor(map { it.product.id }).groupBy({ it.productId }, { it.alias })
            return map { row ->
                ProductCandidate(
                    product = row.product.toDomain(),
                    aliases = aliases[row.product.id].orEmpty(),
                    baseScore = row.product.baseScore,
                    useCount = row.useCount,
                    lastUsedAt = row.lastUsedAt,
                )
            }
        }

        /**
         * Atomically replaces every product of [source] with [products]. Products are upserted
         * (ids are stable, so usage statistics and list items keep pointing at them, whatever the
         * language) and rows left from an older version are deleted afterwards.
         */
        private suspend fun replaceSource(
            source: CatalogSource,
            version: String,
            products: List<CatalogImportProduct>,
        ) {
            transactions.inTransaction {
                dao.deleteAliasesForSource(source.name)
                dao.upsertProducts(
                    products.map {
                        CatalogProductEntity(
                            id = it.id,
                            name = it.name,
                            normalizedName = TextNormalizer.normalize(it.name),
                            category = it.category,
                            brand = null,
                            parentId = it.parentId,
                            source = source,
                            baseScore = it.baseScore,
                            catalogVersion = version,
                            groceryCategory = it.groceryCategory,
                        )
                    },
                )
                dao.deleteOutdated(source.name, version)
                dao.insertAliases(
                    products.flatMap { product ->
                        product.aliases.map { CatalogAliasEntity(productId = product.id, alias = it, normalizedAlias = TextNormalizer.normalize(it)) }
                    },
                )
            }
        }

        private companion object {
            const val CUSTOM_PREFIX = "custom:"
        }
    }
