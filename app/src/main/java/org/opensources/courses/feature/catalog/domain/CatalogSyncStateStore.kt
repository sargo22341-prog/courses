package org.opensources.courses.feature.catalog.domain

import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class CatalogSyncInfo(
    val lastSyncAt: Instant?,
    val version: String?,
)

interface CatalogSyncStateStore {
    val info: Flow<CatalogSyncInfo>

    suspend fun current(): CatalogSyncInfo

    suspend fun markSynced(
        at: Instant,
        version: String?,
    )

    suspend fun seedVersion(): Int

    suspend fun setSeedVersion(version: Int)
}
