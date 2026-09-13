package org.opensources.courses.feature.catalog.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.opensources.courses.testing.FakeCatalogRepository
import org.opensources.courses.testing.FakeConnectivityObserver
import org.opensources.courses.testing.TestNow
import org.opensources.courses.testing.fixedClock
import java.time.Duration
import java.time.Instant

class CatalogSyncManagerTest {
    private val repository = FakeCatalogRepository()
    private val remote = FakeRemoteSource()
    private val store = FakeStateStore()
    private val connectivity = FakeConnectivityObserver(online = true)
    private val manager = CatalogSyncManager(remote, repository, store, connectivity, fixedClock())

    private val products = listOf(CatalogImportProduct("en:milks", "Laits", "Produits laitiers", null, 3))

    @Test
    fun `stale catalog is downloaded with the stored version and imported`() =
        runTest {
            store.state.value = CatalogSyncInfo(TestNow.minus(Duration.ofDays(12)), "etag-1")
            remote.result = RemoteCatalogResult.Updated("etag-2", products)

            val result = manager.syncIfStale()

            assertEquals(CatalogSyncResult.Updated(1), result)
            assertEquals(listOf<String?>("etag-1"), remote.requestedVersions)
            assertEquals("etag-2", repository.importedVersion)
            assertEquals(CatalogSyncInfo(TestNow, "etag-2"), store.state.value)
        }

    @Test
    fun `fresh catalog is not downloaded`() =
        runTest {
            store.state.value = CatalogSyncInfo(TestNow.minus(Duration.ofDays(2)), "etag-1")

            assertEquals(CatalogSyncResult.NotNeeded, manager.syncIfStale())
            assertEquals(emptyList<String?>(), remote.requestedVersions)
        }

    @Test
    fun `offline stale catalog is kept without any request`() =
        runTest {
            connectivity.isOnline.value = false

            assertEquals(CatalogSyncResult.Offline, manager.syncIfStale())
            assertEquals(emptyList<String?>(), remote.requestedVersions)
            assertNull(repository.importedVersion)
        }

    @Test
    fun `forced sync ignores freshness and cache validators`() =
        runTest {
            store.state.value = CatalogSyncInfo(TestNow.minus(Duration.ofHours(1)), "etag-1")
            remote.result = RemoteCatalogResult.Updated("etag-2", products)

            assertEquals(CatalogSyncResult.Updated(1), manager.forceSync())
            assertEquals(listOf<String?>(null), remote.requestedVersions)
            assertEquals(CatalogSyncStatus.Finished(CatalogSyncResult.Updated(1)), manager.status.value)
        }

    @Test
    fun `forced sync while offline reports a clear offline result`() =
        runTest {
            connectivity.isOnline.value = false

            assertEquals(CatalogSyncResult.Offline, manager.forceSync())
            assertEquals(CatalogSyncStatus.Finished(CatalogSyncResult.Offline), manager.status.value)
        }

    @Test
    fun `not modified only refreshes the sync date`() =
        runTest {
            store.state.value = CatalogSyncInfo(TestNow.minus(Duration.ofDays(8)), "etag-1")
            remote.result = RemoteCatalogResult.NotModified

            assertEquals(CatalogSyncResult.UpToDate, manager.syncIfStale())
            assertNull(repository.importedVersion)
            assertEquals(CatalogSyncInfo(TestNow, "etag-1"), store.state.value)
        }

    @Test
    fun `download failure keeps the current catalog and date`() =
        runTest {
            val previous = CatalogSyncInfo(TestNow.minus(Duration.ofDays(9)), "etag-1")
            store.state.value = previous
            remote.failure = CatalogDownloadException("boom")

            assertEquals(CatalogSyncResult.Failed, manager.syncIfStale())
            assertNull(repository.importedVersion)
            assertEquals(previous, store.state.value)
        }

    private class FakeRemoteSource : CatalogRemoteSource {
        var result: RemoteCatalogResult = RemoteCatalogResult.NotModified
        var failure: Exception? = null
        val requestedVersions = mutableListOf<String?>()

        override suspend fun fetch(currentVersion: String?): RemoteCatalogResult {
            requestedVersions += currentVersion
            failure?.let { throw it }
            return result
        }
    }

    private class FakeStateStore : CatalogSyncStateStore {
        val state = MutableStateFlow(CatalogSyncInfo(null, null))
        private var seed = 0
        override val info: Flow<CatalogSyncInfo> = state

        override suspend fun current(): CatalogSyncInfo = state.value

        override suspend fun markSynced(
            at: Instant,
            version: String?,
        ) {
            state.value = CatalogSyncInfo(at, version ?: state.value.version)
        }

        override suspend fun seedVersion(): Int = seed

        override suspend fun setSeedVersion(version: Int) {
            seed = version
        }
    }
}
