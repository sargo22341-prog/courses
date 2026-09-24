package org.opensources.courses.feature.homeassistant.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.opensources.courses.core.sync.ImportableLists
import org.opensources.courses.core.sync.RemoteListChoice
import org.opensources.courses.core.sync.RemoteListImport
import org.opensources.courses.core.sync.SyncCoordinator
import org.opensources.courses.core.sync.SyncFailure
import org.opensources.courses.feature.lists.domain.ShoppingListRepository
import javax.inject.Inject

/**
 * "Importer une liste de Home Assistant", offered when a list is created in the "lists created by this
 * app" mode: in "all lists" mode every list already comes by itself. The chosen list is linked, not
 * copied: it is synchronised both ways like any linked list, and a Mealie list keeps working in Mealie.
 */
class HaRemoteListImport
    @Inject
    constructor(
        private val config: HaConfigRepository,
        private val gateway: HomeAssistantGateway,
        private val links: HaListLinkRepository,
        private val lists: ShoppingListRepository,
        private val sync: SyncCoordinator,
    ) : RemoteListImport {
        override val isAvailable: Flow<Boolean> =
            config.config.map { it.enabled && it.isConfigured && it.listMode == HaListMode.APP_CREATED_ONLY }.distinctUntilChanged()

        /** Read-only lists and lists whose integration is stopped cannot be synchronised: they are left out. */
        override suspend fun importableLists(): ImportableLists {
            val credentials = config.credentials() ?: return ImportableLists.Failed(SyncFailure.UNAUTHORIZED)
            val remote =
                try {
                    gateway.getTodoLists(credentials)
                } catch (exception: HomeAssistantException) {
                    return ImportableLists.Failed(exception.kind.toSyncFailure())
                }
            val linked = lists.observeLists().first().mapNotNull { it.remoteId }.toSet()
            return ImportableLists.Loaded(
                remote
                    .filter { it.isAvailable && it.isEditable && it.entityId !in linked }
                    .map { RemoteListChoice(it.entityId, it.name) },
            )
        }

        override suspend fun importList(list: RemoteListChoice): String = links.importList(list.remoteId, list.name).also { sync.requestSync() }
    }
