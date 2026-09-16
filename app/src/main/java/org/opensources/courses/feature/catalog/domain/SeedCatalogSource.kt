package org.opensources.courses.feature.catalog.domain

import org.opensources.courses.feature.language.domain.AppLanguage

/** The curated catalog bundled with the app: offline, in every [AppLanguage]. */
interface SeedCatalogSource {
    /** Increases whenever the bundled products change; known without reading them. */
    val version: Int

    /** The bundled products named in [language], with the same ids in every language. */
    suspend fun load(language: AppLanguage): List<CatalogImportProduct>
}
