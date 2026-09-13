package org.opensources.courses.feature.catalog.data.seed

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.opensources.courses.core.common.IoDispatcher
import org.opensources.courses.core.database.TransactionRunner
import org.opensources.courses.feature.catalog.data.CatalogRepositoryImpl
import org.opensources.courses.feature.catalog.data.local.CatalogDao
import org.opensources.courses.feature.catalog.domain.CatalogImportProduct
import org.opensources.courses.feature.catalog.domain.CatalogSource
import org.opensources.courses.feature.catalog.domain.CatalogSyncStateStore
import org.opensources.courses.feature.catalog.domain.TextNormalizer
import javax.inject.Inject

@Serializable
data class SeedCatalogDto(
    val version: Int,
    val categories: List<SeedCategoryDto>,
)

@Serializable
data class SeedCategoryDto(
    val name: String,
    val products: List<SeedProductDto>,
)

@Serializable
data class SeedProductDto(
    val name: String,
    val aliases: List<String> = emptyList(),
    val variants: List<String> = emptyList(),
)

/**
 * Imports the curated French catalog bundled in `assets/catalog/seed_fr.json`, so autocomplete
 * works offline from the first launch, before any OpenFoodFacts download. Re-imported only when
 * the asset version increases.
 */
class SeedCatalogLoader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val json: Json,
        private val dao: CatalogDao,
        private val transactions: TransactionRunner,
        private val stateStore: CatalogSyncStateStore,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        @OptIn(ExperimentalSerializationApi::class)
        suspend fun loadIfNeeded() {
            withContext(ioDispatcher) {
                val seed = context.assets.open(ASSET_PATH).use { json.decodeFromStream(SeedCatalogDto.serializer(), it) }
                if (stateStore.seedVersion() >= seed.version) return@withContext
                CatalogRepositoryImpl.replaceSource(
                    dao = dao,
                    transactions = transactions,
                    source = CatalogSource.SEED,
                    version = "seed-${seed.version}",
                    products = SeedCatalogMapper.map(seed),
                )
                stateStore.setSeedVersion(seed.version)
            }
        }

        private companion object {
            const val ASSET_PATH = "catalog/seed_fr.json"
        }
    }

object SeedCatalogMapper {
    private const val PRODUCT_SCORE = 8
    private const val VARIANT_SCORE = 6

    fun map(seed: SeedCatalogDto): List<CatalogImportProduct> =
        seed.categories
            .flatMap { category ->
                category.products.flatMap { product ->
                    val parentId = idFor(product.name)
                    listOf(
                        CatalogImportProduct(parentId, product.name, category.name, null, PRODUCT_SCORE, product.aliases),
                    ) +
                        product.variants.map { variant ->
                            CatalogImportProduct(idFor(variant), variant, category.name, parentId, VARIANT_SCORE)
                        }
                }
            }.distinctBy { it.id }

    fun idFor(name: String): String = "seed:" + TextNormalizer.normalize(name).replace(' ', '-')
}
