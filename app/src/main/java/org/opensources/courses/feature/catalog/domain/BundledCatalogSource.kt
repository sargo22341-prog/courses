package org.opensources.courses.feature.catalog.domain

import org.opensources.courses.feature.language.domain.AppLanguage

/**
 * A part of the catalog bundled in the assets of the application: complete from the very first
 * launch, in every language, without any download
 * (see docs/adr/0025-catalogue-genere-a-la-compilation.md).
 */
interface BundledCatalogSource {
    /** The rows of the catalog these products replace. */
    val source: CatalogSource

    /** Increases whenever the bundled products change; known without reading them. */
    val version: Int

    /** The bundled products named in [language], with the same ids in every language. */
    suspend fun load(language: AppLanguage): List<CatalogImportProduct>
}
