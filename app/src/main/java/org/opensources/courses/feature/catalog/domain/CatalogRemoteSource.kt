package org.opensources.courses.feature.catalog.domain

import org.opensources.courses.feature.language.domain.AppLanguage

interface CatalogRemoteSource {
    /**
     * Version of what [fetch] extracts from the downloaded file. It increases when the import
     * produces new data (shop sections…), so a catalog imported by an older version of the app is
     * downloaded again once, even if it is recent.
     */
    val formatVersion: Int

    /**
     * Downloads the catalog with product names in [language]. When [currentVersion] is given and
     * unchanged remotely, returns [RemoteCatalogResult.NotModified] without downloading it again.
     *
     * @throws CatalogDownloadException on network or format errors.
     */
    suspend fun fetch(
        currentVersion: String?,
        language: AppLanguage,
    ): RemoteCatalogResult
}

sealed interface RemoteCatalogResult {
    data object NotModified : RemoteCatalogResult

    data class Updated(
        val version: String,
        val products: List<CatalogImportProduct>,
    ) : RemoteCatalogResult
}

class CatalogDownloadException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
