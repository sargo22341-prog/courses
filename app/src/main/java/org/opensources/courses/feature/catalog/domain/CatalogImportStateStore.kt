package org.opensources.courses.feature.catalog.domain

import org.opensources.courses.feature.language.domain.AppLanguage

/** Version (0 before any import) and language of a bundled catalog already stored in the database. */
data class CatalogImportInfo(
    val version: Int,
    val language: AppLanguage,
)

/** Remembers what each [BundledCatalogSource] last wrote, so an unchanged asset is never read again. */
interface CatalogImportStateStore {
    suspend fun imported(source: CatalogSource): CatalogImportInfo

    suspend fun markImported(
        source: CatalogSource,
        version: Int,
        language: AppLanguage,
    )
}
