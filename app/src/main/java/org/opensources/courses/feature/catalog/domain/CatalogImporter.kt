package org.opensources.courses.feature.catalog.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opensources.courses.feature.language.domain.AppLanguageRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the local catalog in the language of the application. Every source is bundled in the assets:
 * the import needs no network, cannot fail halfway through a download and gives the same catalog to
 * everyone. A source is read only when its version or the language changed, not at every start.
 */
@Singleton
class CatalogImporter
    @Inject
    constructor(
        // Kotlin compiles List<T> to List<? extends T>, which Dagger does not match with the binding.
        private val sources: List<@JvmSuppressWildcards BundledCatalogSource>,
        private val repository: CatalogRepository,
        private val stateStore: CatalogImportStateStore,
        private val languages: AppLanguageRepository,
    ) {
        private val mutex = Mutex()
        private val mutableRevision = MutableStateFlow(0)

        /** Increases after every import that replaced products. */
        val revision: StateFlow<Int> = mutableRevision.asStateFlow()

        /** Imports the sources whose version or language changed; returns whether any was imported. */
        suspend fun importIfNeeded(): Boolean =
            mutex.withLock {
                val language = languages.language.value
                var imported = false
                for (source in sources) {
                    val stored = stateStore.imported(source.source)
                    if (stored.version >= source.version && stored.language == language) continue
                    repository.replaceCatalog(source.source, versionOf(source, language.tag), source.load(language))
                    stateStore.markImported(source.source, source.version, language)
                    imported = true
                }
                if (imported) mutableRevision.update { it + 1 }
                imported
            }

        /** Tells imports apart inside a source: rows of the previous language are deleted by the same import. */
        private fun versionOf(
            source: BundledCatalogSource,
            languageTag: String,
        ): String = "${source.source.name.lowercase()}-${source.version}-$languageTag"
    }
