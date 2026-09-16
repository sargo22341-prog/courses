package org.opensources.courses.feature.catalog.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opensources.courses.core.network.ConnectivityObserver
import org.opensources.courses.feature.language.domain.AppLanguage
import org.opensources.courses.feature.language.domain.AppLanguageRepository
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
 * No backend: the app itself keeps its local catalog in the app language. The bundled catalog is
 * imported at once, offline, when it changes or when the language changes. The OpenFoodFacts catalog
 * is refreshed when it is older than a week and a network is available; one imported in an older
 * format or in another language is refreshed at once. The user can also force a refresh.
 */
@Singleton
class CatalogSyncManager
    @Inject
    constructor(
        private val remote: CatalogRemoteSource,
        private val seed: SeedCatalogSource,
        private val repository: CatalogRepository,
        private val stateStore: CatalogSyncStateStore,
        private val languages: AppLanguageRepository,
        private val connectivity: ConnectivityObserver,
        private val clock: Clock,
    ) {
        private val policy = CatalogFreshnessPolicy()
        private val mutex = Mutex()
        private val seedMutex = Mutex()
        private val mutableStatus = MutableStateFlow<CatalogSyncStatus>(CatalogSyncStatus.Idle)
        val status: StateFlow<CatalogSyncStatus> = mutableStatus.asStateFlow()

        private val mutableRevision = MutableStateFlow(0)

        /** Increases after every import that replaced products, bundled or downloaded. */
        val revision: StateFlow<Int> = mutableRevision.asStateFlow()

        /**
         * Imports the bundled catalog when its version or the app language changed; returns whether it
         * did. The bundled file is only read in that case, not at every start.
         */
        suspend fun importSeedIfNeeded(): Boolean =
            seedMutex.withLock {
                val language = languages.language.value
                val version = seed.version
                val imported = stateStore.seedImport()
                if (imported.version >= version && imported.language == language) return@withLock false
                repository.replaceSeedCatalog("seed-$version-${language.tag}", seed.load(language))
                stateStore.markSeedImported(version, language)
                mutableRevision.update { it + 1 }
                true
            }

        suspend fun syncIfStale(): CatalogSyncResult {
            val info = stateStore.current()
            val language = languages.language.value
            // Its ETag has not changed, but its import lacks data this version extracts (shop sections)
            // or names products in another language.
            val importAgain = info.formatVersion < remote.formatVersion || info.language != language
            if (!importAgain && !policy.isStale(info.lastSyncAt, clock.instant())) return CatalogSyncResult.NotNeeded
            if (!connectivity.isOnline.value) return CatalogSyncResult.Offline
            return synchronize(currentVersion = if (importAgain) null else info.version, language)
        }

        /** Ignores the cache validators and downloads the catalog again, in the app language. */
        suspend fun forceSync(): CatalogSyncResult {
            if (!connectivity.isOnline.value) {
                return CatalogSyncResult.Offline.also { mutableStatus.value = CatalogSyncStatus.Finished(it) }
            }
            return synchronize(currentVersion = null, languages.language.value)
        }

        private suspend fun synchronize(
            currentVersion: String?,
            language: AppLanguage,
        ): CatalogSyncResult =
            mutex.withLock {
                mutableStatus.value = CatalogSyncStatus.Running
                val result =
                    try {
                        when (val fetched = remote.fetch(currentVersion, language)) {
                            RemoteCatalogResult.NotModified -> {
                                stateStore.markSynced(clock.instant(), currentVersion, formatVersion = null, language)
                                CatalogSyncResult.UpToDate
                            }
                            is RemoteCatalogResult.Updated -> {
                                repository.replaceRemoteCatalog(fetched.version, fetched.products)
                                stateStore.markSynced(clock.instant(), fetched.version, remote.formatVersion, language)
                                mutableRevision.update { it + 1 }
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
