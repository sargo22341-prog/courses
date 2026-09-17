package org.opensources.courses

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.opensources.courses.core.common.ApplicationScope
import org.opensources.courses.core.sync.SyncCoordinator
import org.opensources.courses.feature.catalog.domain.CatalogImporter
import org.opensources.courses.feature.language.domain.AppLanguageRepository
import org.opensources.courses.feature.onboarding.domain.KeepFrenchForExistingInstallUseCase
import org.opensources.courses.feature.shopping.domain.LinkItemsToCatalogUseCase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Background work started with the process. Nothing here blocks the UI: lists are displayed from
 * Room immediately while the catalog import and Home Assistant synchronisation run on their own.
 */
@Singleton
class AppInitializer
    @Inject
    constructor(
        private val keepFrenchForExistingInstall: KeepFrenchForExistingInstallUseCase,
        private val languages: AppLanguageRepository,
        private val catalogImporter: CatalogImporter,
        private val linkItemsToCatalog: LinkItemsToCatalogUseCase,
        private val syncCoordinator: SyncCoordinator,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        fun start() {
            scope.launch {
                // Before the catalog is prepared, so that an existing install does not switch language first.
                ignoringFailures { keepFrenchForExistingInstall() }
                // Items already in the lists are matched again with the products of every import.
                launch { catalogImporter.revision.collectLatest { ignoringFailures { linkItemsToCatalog() } } }
                // The whole catalog is bundled: it is imported at once, offline, in the app language and
                // again whenever it changes.
                languages.language.collectLatest { ignoringFailures { catalogImporter.importIfNeeded() } }
            }
            syncCoordinator.start()
        }

        /** A failed background refresh must never crash the app: the local data stays usable. */
        private suspend fun ignoringFailures(block: suspend () -> Unit) {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Retried at the next start.
            }
        }
    }
