package org.opensources.courses.feature.catalog.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opensources.courses.core.network.ConnectivityObserver
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CatalogSyncResult {
    data class Updated(
        val productCount: Int,
    ) : CatalogSyncResult

    /** The server confirmed the local version is current. */
    data object UpToDate : CatalogSyncResult

    /** Weekly check: the catalog is recent enough, nothing was downloaded. */
    data object NotNeeded : CatalogSyncResult

    data object Offline : CatalogSyncResult

    /** Download or import failed; the previous catalog is untouched. */
    data object Failed : CatalogSyncResult
}

sealed interface CatalogSyncStatus {
    data object Idle : CatalogSyncStatus

    data object Running : CatalogSyncStatus

    data class Finished(
        val result: CatalogSyncResult,
    ) : CatalogSyncStatus
}

/**
 * No backend: the app itself checks the age of its local catalog when it opens and refreshes it
 * from OpenFoodFacts when it is older than a week and a network is available. The user can also
 * force a refresh from the settings.
 */
@Singleton
class CatalogSyncManager
    @Inject
    constructor(
        private val remote: CatalogRemoteSource,
        private val repository: CatalogRepository,
        private val stateStore: CatalogSyncStateStore,
        private val connectivity: ConnectivityObserver,
        private val clock: Clock,
    ) {
        private val policy = CatalogFreshnessPolicy()
        private val mutex = Mutex()
        private val mutableStatus = MutableStateFlow<CatalogSyncStatus>(CatalogSyncStatus.Idle)
        val status: StateFlow<CatalogSyncStatus> = mutableStatus.asStateFlow()

        suspend fun syncIfStale(): CatalogSyncResult {
            val info = stateStore.current()
            if (!policy.isStale(info.lastSyncAt, clock.instant())) return CatalogSyncResult.NotNeeded
            if (!connectivity.isOnline.value) return CatalogSyncResult.Offline
            return synchronize(info.version)
        }

        /** Ignores the cache validators and downloads the catalog again. */
        suspend fun forceSync(): CatalogSyncResult {
            if (!connectivity.isOnline.value) {
                return CatalogSyncResult.Offline.also { mutableStatus.value = CatalogSyncStatus.Finished(it) }
            }
            return synchronize(currentVersion = null)
        }

        private suspend fun synchronize(currentVersion: String?): CatalogSyncResult =
            mutex.withLock {
                mutableStatus.value = CatalogSyncStatus.Running
                val result =
                    try {
                        when (val fetched = remote.fetch(currentVersion)) {
                            RemoteCatalogResult.NotModified -> {
                                stateStore.markSynced(clock.instant(), currentVersion)
                                CatalogSyncResult.UpToDate
                            }
                            is RemoteCatalogResult.Updated -> {
                                repository.replaceRemoteCatalog(fetched.version, fetched.products)
                                stateStore.markSynced(clock.instant(), fetched.version)
                                CatalogSyncResult.Updated(fetched.products.size)
                            }
                        }
                    } catch (cancellation: CancellationException) {
                        mutableStatus.value = CatalogSyncStatus.Idle
                        throw cancellation
                    } catch (_: Exception) {
                        CatalogSyncResult.Failed
                    }
                mutableStatus.value = CatalogSyncStatus.Finished(result)
                result
            }
    }
