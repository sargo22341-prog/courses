package org.opensources.courses.feature.catalog.data.taxonomy

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.opensources.courses.core.common.IoDispatcher
import org.opensources.courses.feature.catalog.domain.BundledCatalogSource
import org.opensources.courses.feature.catalog.domain.CatalogImportProduct
import org.opensources.courses.feature.catalog.domain.CatalogSource
import org.opensources.courses.feature.language.domain.AppLanguage
import javax.inject.Inject

/**
 * Reads the generated OpenFoodFacts catalog bundled in `assets/catalog/taxonomy-<language>.json`.
 * Only the file of the application language is read; the others stay compressed in the package.
 */
class AssetTaxonomyCatalogSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val json: Json,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : BundledCatalogSource {
        override val source: CatalogSource = CatalogSource.OPEN_FOOD_FACTS

        override val version: Int = VERSION

        @OptIn(ExperimentalSerializationApi::class)
        override suspend fun load(language: AppLanguage): List<CatalogImportProduct> =
            withContext(ioDispatcher) {
                context.assets
                    .open(assetPath(language))
                    .use { json.decodeFromStream(TaxonomyCatalogDto.serializer(), it) }
                    .products
                    .map { it.toDomain() }
            }

        companion object {
            /**
             * `version` of the generated files, known without decoding them: a file is only read when
             * it must be imported. `scripts/generate-catalog.py` reads this line and increases it,
             * here and in the files it writes, when the products it generates really changed.
             */
            const val VERSION = 1

            fun assetPath(language: AppLanguage): String = "catalog/taxonomy-${language.tag}.json"
        }
    }
