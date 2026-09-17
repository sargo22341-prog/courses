package org.opensources.courses.feature.catalog.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.courses.feature.language.domain.AppLanguage
import org.opensources.courses.testing.FakeAppLanguageRepository
import org.opensources.courses.testing.FakeCatalogRepository

class CatalogImporterTest {
    private val repository = FakeCatalogRepository()
    private val seed = FakeBundledSource(CatalogSource.SEED, version = 3, name = "Lait")
    private val taxonomy = FakeBundledSource(CatalogSource.OPEN_FOOD_FACTS, version = 1, name = "Laits")
    private val store = FakeStateStore()
    private val languages = FakeAppLanguageRepository(AppLanguage.FRENCH)
    private val importer = CatalogImporter(listOf(seed, taxonomy), repository, store, languages)

    @Test
    fun `a first start imports every bundled catalog in the app language`() =
        runTest {
            assertTrue(importer.importIfNeeded())

            assertEquals(listOf(AppLanguage.FRENCH), seed.requested)
            assertEquals(listOf(AppLanguage.FRENCH), taxonomy.requested)
            assertEquals("seed-3-fr", repository.importedVersions[CatalogSource.SEED])
            assertEquals("open_food_facts-1-fr", repository.importedVersions[CatalogSource.OPEN_FOOD_FACTS])
            assertEquals(CatalogImportInfo(1, AppLanguage.FRENCH), store.infos[CatalogSource.OPEN_FOOD_FACTS])
            assertEquals(1, importer.revision.value)
        }

    @Test
    fun `nothing is read again when every catalog is already imported in the app language`() =
        runTest {
            store.infos[CatalogSource.SEED] = CatalogImportInfo(3, AppLanguage.FRENCH)
            store.infos[CatalogSource.OPEN_FOOD_FACTS] = CatalogImportInfo(1, AppLanguage.FRENCH)

            assertFalse(importer.importIfNeeded())

            // Nothing to import: the bundled files are not even read.
            assertTrue(seed.requested.isEmpty())
            assertTrue(taxonomy.requested.isEmpty())
            assertTrue(repository.importedVersions.isEmpty())
            assertEquals(0, importer.revision.value)
        }

    @Test
    fun `a language change imports every catalog again, offline`() =
        runTest {
            store.infos[CatalogSource.SEED] = CatalogImportInfo(3, AppLanguage.FRENCH)
            store.infos[CatalogSource.OPEN_FOOD_FACTS] = CatalogImportInfo(1, AppLanguage.FRENCH)
            languages.setLanguage(AppLanguage.PORTUGUESE)

            assertTrue(importer.importIfNeeded())

            assertEquals(listOf(AppLanguage.PORTUGUESE), taxonomy.requested)
            assertEquals("open_food_facts-1-pt", repository.importedVersions[CatalogSource.OPEN_FOOD_FACTS])
            assertEquals(CatalogImportInfo(3, AppLanguage.PORTUGUESE), store.infos[CatalogSource.SEED])
            assertEquals(1, importer.revision.value)
        }

    @Test
    fun `only the catalog whose version increased is imported again`() =
        runTest {
            store.infos[CatalogSource.SEED] = CatalogImportInfo(3, AppLanguage.FRENCH)
            store.infos[CatalogSource.OPEN_FOOD_FACTS] = CatalogImportInfo(0, AppLanguage.FRENCH)

            assertTrue(importer.importIfNeeded())

            assertTrue(seed.requested.isEmpty())
            assertEquals(listOf(AppLanguage.FRENCH), taxonomy.requested)
            assertEquals(setOf(CatalogSource.OPEN_FOOD_FACTS), repository.importedVersions.keys)
        }

    @Test
    fun `a failing source leaves the catalogs imported before it untouched`() =
        runTest {
            taxonomy.failure = IllegalStateException("asset unreadable")

            runCatching { importer.importIfNeeded() }

            assertEquals("seed-3-fr", repository.importedVersions[CatalogSource.SEED])
            assertEquals(CatalogImportInfo(3, AppLanguage.FRENCH), store.infos[CatalogSource.SEED])
            assertFalse(CatalogSource.OPEN_FOOD_FACTS in store.infos)
        }

    private class FakeBundledSource(
        override val source: CatalogSource,
        override val version: Int,
        private val name: String,
    ) : BundledCatalogSource {
        val requested = mutableListOf<AppLanguage>()
        var failure: Exception? = null

        override suspend fun load(language: AppLanguage): List<CatalogImportProduct> {
            requested += language
            failure?.let { throw it }
            return listOf(CatalogImportProduct("${source.name.lowercase()}:milk", name, null, 3))
        }
    }

    private class FakeStateStore : CatalogImportStateStore {
        val infos = mutableMapOf<CatalogSource, CatalogImportInfo>()

        override suspend fun imported(source: CatalogSource): CatalogImportInfo =
            infos[source] ?: CatalogImportInfo(0, AppLanguage.FRENCH)

        override suspend fun markImported(
            source: CatalogSource,
            version: Int,
            language: AppLanguage,
        ) {
            infos[source] = CatalogImportInfo(version, language)
        }
    }
}
