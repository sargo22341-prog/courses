package org.opensources.courses.feature.catalog.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.courses.feature.language.domain.AppLanguage
import org.opensources.courses.testing.FakeAppLanguageRepository
import org.opensources.courses.testing.FakeCatalogRepository
import org.opensources.courses.testing.FakeConnectivityObserver
import org.opensources.courses.testing.TestNow
import org.opensources.courses.testing.fixedClock
import java.time.Duration
import java.time.Instant

class CatalogSyncManagerTest {
    private val repository = FakeCatalogRepository()
    private val remote = FakeRemoteSource()
    private val seed = FakeSeedSource()
    private val store = FakeStateStore()
    private val languages = FakeAppLanguageRepository(AppLanguage.FRENCH)
    private val connectivity = FakeConnectivityObserver(online = true)
    private val manager = CatalogSyncManager(remote, seed, repository, store, languages, connectivity, fixedClock())

    private val products = listOf(CatalogImportProduct("en:milks", "Laits", "Produits laitiers", 3))

    /** A catalog imported by the current version of the app, [age] ago. */
    private fun imported(
        age: Duration,
        version: String,
        language: AppLanguage = AppLanguage.FRENCH,
    ) = CatalogSyncInfo(TestNow.minus(age), version, CURRENT_FORMAT, language)

    @Test
    fun `stale catalog is downloaded with the stored version and imported`() =
        runTest {
            store.state.value = imported(Duration.ofDays(12), "etag-1")
            remote.result = RemoteCatalogResult.Updated("etag-2", products)

            val result = manager.syncIfStale()

            assertEquals(CatalogSyncResult.Updated(1), result)
            assertEquals(listOf<Pair<String?, AppLanguage>>("etag-1" to AppLanguage.FRENCH), remote.requests)
            assertEquals("etag-2", repository.importedVersion)
            assertEquals(CatalogSyncInfo(TestNow, "etag-2", CURRENT_FORMAT, AppLanguage.FRENCH), store.state.value)
            assertEquals(1, manager.revision.value)
        }

    @Test
    fun `fresh catalog is not downloaded`() =
        runTest {
            store.state.value = imported(Duration.ofDays(2), "etag-1")

            assertEquals(CatalogSyncResult.NotNeeded, manager.syncIfStale())
            assertEquals(emptyList<Pair<String?, AppLanguage>>(), remote.requests)
        }

    @Test
    fun `fresh catalog imported in an older format is downloaded again in full`() =
        runTest {
            store.state.value = CatalogSyncInfo(TestNow.minus(Duration.ofDays(1)), "etag-1", formatVersion = 0, AppLanguage.FRENCH)
            remote.result = RemoteCatalogResult.Updated("etag-1", products)

            assertEquals(CatalogSyncResult.Updated(1), manager.syncIfStale())
            // Without the ETag, otherwise the server would answer "not modified".
            assertEquals(listOf<Pair<String?, AppLanguage>>(null to AppLanguage.FRENCH), remote.requests)
            assertEquals(CURRENT_FORMAT, store.state.value.formatVersion)
        }

    @Test
    fun `fresh catalog in another language is downloaded again in full in the app language`() =
        runTest {
            store.state.value = imported(Duration.ofHours(3), "etag-1", AppLanguage.FRENCH)
            languages.setLanguage(AppLanguage.GERMAN)
            remote.result = RemoteCatalogResult.Updated("etag-1", products)

            assertEquals(CatalogSyncResult.Updated(1), manager.syncIfStale())
            // The file is the same in every language: its ETag would make the server answer "not modified".
            assertEquals(listOf<Pair<String?, AppLanguage>>(null to AppLanguage.GERMAN), remote.requests)
            assertEquals(AppLanguage.GERMAN, store.state.value.language)
            assertEquals(1, manager.revision.value)
        }

    @Test
    fun `offline stale catalog is kept without any request`() =
        runTest {
            connectivity.isOnline.value = false

            assertEquals(CatalogSyncResult.Offline, manager.syncIfStale())
            assertEquals(emptyList<Pair<String?, AppLanguage>>(), remote.requests)
            assertNull(repository.importedVersion)
        }

    @Test
    fun `language changed offline keeps the previous catalog until a network is available`() =
        runTest {
            store.state.value = imported(Duration.ofHours(3), "etag-1", AppLanguage.FRENCH)
            languages.setLanguage(AppLanguage.SPANISH)
            connectivity.isOnline.value = false

            assertEquals(CatalogSyncResult.Offline, manager.syncIfStale())
            assertNull(repository.importedVersion)
            assertEquals(AppLanguage.FRENCH, store.state.value.language)
        }

    @Test
    fun `forced sync ignores freshness and cache validators and uses the app language`() =
        runTest {
            store.state.value = imported(Duration.ofHours(1), "etag-1")
            languages.setLanguage(AppLanguage.ITALIAN)
            remote.result = RemoteCatalogResult.Updated("etag-2", products)

            assertEquals(CatalogSyncResult.Updated(1), manager.forceSync())
            assertEquals(listOf<Pair<String?, AppLanguage>>(null to AppLanguage.ITALIAN), remote.requests)
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
            store.state.value = imported(Duration.ofDays(8), "etag-1")
            remote.result = RemoteCatalogResult.NotModified

            assertEquals(CatalogSyncResult.UpToDate, manager.syncIfStale())
            assertNull(repository.importedVersion)
            assertEquals(CatalogSyncInfo(TestNow, "etag-1", CURRENT_FORMAT, AppLanguage.FRENCH), store.state.value)
            assertEquals(0, manager.revision.value)
        }

    @Test
    fun `download failure keeps the current catalog and date`() =
        runTest {
            val previous = imported(Duration.ofDays(9), "etag-1")
            store.state.value = previous
            remote.failure = CatalogDownloadException("boom")

            assertEquals(CatalogSyncResult.Failed, manager.syncIfStale())
            assertNull(repository.importedVersion)
            assertEquals(previous, store.state.value)
            assertEquals(0, manager.revision.value)
        }

    @Test
    fun `bundled catalog already imported in the app language is not imported again`() =
        runTest {
            store.seed = SeedImportInfo(SEED_VERSION, AppLanguage.FRENCH)

            assertFalse(manager.importSeedIfNeeded())
            assertNull(repository.seedVersion)
            // Nothing to import: the bundled file is not even read.
            assertTrue(seed.requested.isEmpty())
            assertEquals(0, manager.revision.value)
        }

    @Test
    fun `bundled catalog is imported again offline when the language changes`() =
        runTest {
            store.seed = SeedImportInfo(SEED_VERSION, AppLanguage.FRENCH)
            connectivity.isOnline.value = false
            languages.setLanguage(AppLanguage.PORTUGUESE)

            assertTrue(manager.importSeedIfNeeded())
            assertEquals(AppLanguage.PORTUGUESE, seed.requested.single())
            assertEquals("seed-$SEED_VERSION-pt", repository.seedVersion)
            assertEquals(SeedImportInfo(SEED_VERSION, AppLanguage.PORTUGUESE), store.seed)
            assertEquals(1, manager.revision.value)
        }

    @Test
    fun `bundled catalog is imported when its version increases`() =
        runTest {
            store.seed = SeedImportInfo(SEED_VERSION - 1, AppLanguage.FRENCH)

            assertTrue(manager.importSeedIfNeeded())
            assertEquals(SeedImportInfo(SEED_VERSION, AppLanguage.FRENCH), store.seed)
        }

    private class FakeRemoteSource : CatalogRemoteSource {
        var result: RemoteCatalogResult = RemoteCatalogResult.NotModified
        var failure: Exception? = null
        val requests = mutableListOf<Pair<String?, AppLanguage>>()

        override val formatVersion: Int = CURRENT_FORMAT

        override suspend fun fetch(
            currentVersion: String?,
            language: AppLanguage,
        ): RemoteCatalogResult {
            requests += currentVersion to language
            failure?.let { throw it }
            return result
        }
    }

    private class FakeSeedSource : SeedCatalogSource {
        val requested = mutableListOf<AppLanguage>()

        override val version: Int = SEED_VERSION

        override suspend fun load(language: AppLanguage): List<CatalogImportProduct> {
            requested += language
            return listOf(CatalogImportProduct("seed:lait", "Leite", "Laticínios", 8))
        }
    }

    private class FakeStateStore : CatalogSyncStateStore {
        val state = MutableStateFlow(CatalogSyncInfo(null, null, 0, AppLanguage.FRENCH))
        var seed = SeedImportInfo(0, AppLanguage.FRENCH)
        override val info: Flow<CatalogSyncInfo> = state

        override suspend fun current(): CatalogSyncInfo = state.value

        override suspend fun markSynced(
            at: Instant,
            version: String?,
            formatVersion: Int?,
            language: AppLanguage,
        ) {
            state.value = CatalogSyncInfo(at, version ?: state.value.version, formatVersion ?: state.value.formatVersion, language)
        }

        override suspend fun seedImport(): SeedImportInfo = seed

        override suspend fun markSeedImported(
            version: Int,
            language: AppLanguage,
        ) {
            seed = SeedImportInfo(version, language)
        }
    }

    private companion object {
        const val CURRENT_FORMAT = 3
        const val SEED_VERSION = 3
    }
}
