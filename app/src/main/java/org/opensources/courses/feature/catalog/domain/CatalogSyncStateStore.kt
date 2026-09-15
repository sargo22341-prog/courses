package org.opensources.courses.feature.catalog.domain

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** @property formatVersion [CatalogRemoteSource.formatVersion] of the last import, 0 before any. */
data class CatalogSyncInfo(
    val lastSyncAt: Instant?,
    val version: String?,
    val formatVersion: Int = 0,
)

interface CatalogSyncStateStore {
    val info: Flow<CatalogSyncInfo>

    suspend fun current(): CatalogSyncInfo

    /** A null [version] or [formatVersion] keeps the stored value. */
    suspend fun markSynced(
        at: Instant,
        version: String?,
        formatVersion: Int?,
    )

    suspend fun seedVersion(): Int

    suspend fun setSeedVersion(version: Int)
}
