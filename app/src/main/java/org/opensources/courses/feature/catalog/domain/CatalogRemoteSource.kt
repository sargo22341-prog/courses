package org.opensources.courses.feature.catalog.domain

interface CatalogRemoteSource {
    /**
     * Downloads the catalog. When [currentVersion] is given and unchanged remotely, returns
     * [RemoteCatalogResult.NotModified] without downloading it again.
     *
     * @throws CatalogDownloadException on network or format errors.
     */
    suspend fun fetch(currentVersion: String?): RemoteCatalogResult
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
