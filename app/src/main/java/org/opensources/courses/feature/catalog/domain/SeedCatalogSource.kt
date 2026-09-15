package org.opensources.courses.feature.catalog.domain

import org.opensources.courses.feature.language.domain.AppLanguage

/** The curated catalog bundled with the app: offline, in every [AppLanguage]. */
interface SeedCatalogSource {
    suspend fun load(language: AppLanguage): SeedCatalog
}

/**
 * @property version increases whenever the bundled products change.
 * @property products the same products, with the same ids, in every language.
 */
data class SeedCatalog(
    val version: Int,
    val products: List<CatalogImportProduct>,
)
