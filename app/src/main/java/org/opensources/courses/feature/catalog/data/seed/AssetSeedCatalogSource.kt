package org.opensources.courses.feature.catalog.data.seed

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.opensources.courses.core.common.IoDispatcher
import org.opensources.courses.feature.catalog.domain.CatalogImportProduct
import org.opensources.courses.feature.catalog.domain.SeedCatalogSource
import org.opensources.courses.feature.language.domain.AppLanguage
import javax.inject.Inject

/**
 * Reads the curated catalog bundled in `assets/catalog/seed.json`, so autocomplete works offline
 * from the first launch, in every language, before any OpenFoodFacts download.
 */
class AssetSeedCatalogSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val json: Json,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : SeedCatalogSource {
        override val version: Int = VERSION

        @OptIn(ExperimentalSerializationApi::class)
        override suspend fun load(language: AppLanguage): List<CatalogImportProduct> =
            withContext(ioDispatcher) {
                val seed = context.assets.open(ASSET_PATH).use { json.decodeFromStream(SeedCatalogDto.serializer(), it) }
                SeedCatalogMapper.map(seed, language)
            }

        companion object {
            /**
             * `version` of the asset, known without decoding it: the file is only read when it must
             * be imported. Increase both together (guarded by a test).
             */
            const val VERSION = 3

            const val ASSET_PATH = "catalog/seed.json"
        }
    }
