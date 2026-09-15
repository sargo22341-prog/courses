package org.opensources.courses.feature.catalog.domain

import kotlinx.coroutines.flow.Flow
import org.opensources.courses.feature.language.domain.AppLanguage
import java.time.Instant

/**
 * @property formatVersion [CatalogRemoteSource.formatVersion] of the last import, 0 before any.
 * @property language language of the product names of the last import.
 */
data class CatalogSyncInfo(
    val lastSyncAt: Instant?,
    val version: String?,
    val formatVersion: Int,
    val language: AppLanguage,
)

/** Version (0 before any import) and language of the bundled catalog stored in the database. */
data class SeedImportInfo(
    val version: Int,
    val language: AppLanguage,
)

interface CatalogSyncStateStore {
    val info: Flow<CatalogSyncInfo>

    suspend fun current(): CatalogSyncInfo

    /** A null [version] or [formatVersion] keeps the stored value. */
    suspend fun markSynced(
        at: Instant,
        version: String?,
        formatVersion: Int?,
        language: AppLanguage,
    )

    suspend fun seedImport(): SeedImportInfo

    suspend fun markSeedImported(
        version: Int,
        language: AppLanguage,
    )
}
