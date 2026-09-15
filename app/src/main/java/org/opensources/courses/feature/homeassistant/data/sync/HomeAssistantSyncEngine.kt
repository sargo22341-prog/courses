package org.opensources.courses.feature.homeassistant.data.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import org.opensources.courses.core.sync.RemoteSyncEngine
import org.opensources.courses.core.sync.SyncFailure
import org.opensources.courses.core.sync.SyncOperation
import org.opensources.courses.core.sync.SyncOperationType
import org.opensources.courses.core.sync.SyncOutcome
import org.opensources.courses.core.sync.SyncQueue
import org.opensources.courses.feature.catalog.domain.CatalogRepository
import org.opensources.courses.feature.homeassistant.domain.HaConfigRepository
import org.opensources.courses.feature.homeassistant.domain.HaCredentials
import org.opensources.courses.feature.homeassistant.domain.HaErrorKind
import org.opensources.courses.feature.homeassistant.domain.HaListMode
import org.opensources.courses.feature.homeassistant.domain.HaLiveUpdates
import org.opensources.courses.feature.homeassistant.domain.HaTodoList
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantException
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantGateway
import org.opensources.courses.feature.language.domain.AppLanguageRepository
import javax.inject.Inject

/**
 * Bidirectional synchronisation with Home Assistant to-do lists.
 *
 * Lists first: deletions of lists removed in the app are sent. In "all lists" mode, every editable
 * Home Assistant list missing from the app is then imported, and a list deleted in Home Assistant
 * leaves the app; in "lists created by the app" mode, lists imported earlier are removed and a list
 * deleted in Home Assistant is only unlinked.
 *
 * Then, for each synchronised list:
 * 1. create the remote list if a `CREATE_LIST` operation is pending;
 * 2. read remote items and give local items without uid the uid of a same-named remote item;
 * 3. push pending item operations ([HaItemPusher]);
 * 4. read remote items again and apply them ([HaItemReconciler], [ConflictResolver] rules).
 *
 * `UPDATE_LIST` (rename) cannot be sent: the Home Assistant REST API offers no way to rename a to-do
 * entity, so the new name stays local. A rename made in Home Assistant renames imported lists.
 * A linked list that is unavailable in Home Assistant is skipped: its changes stay queued.
 */
class HomeAssistantSyncEngine
    @Inject
    constructor(
        private val config: HaConfigRepository,
        private val gateway: HomeAssistantGateway,
        private val store: SyncLocalStore,
        private val queue: SyncQueue,
        catalog: CatalogRepository,
        private val liveUpdates: HaLiveUpdates,
        languages: AppLanguageRepository,
    ) : RemoteSyncEngine {
        private val pusher = HaItemPusher(gateway, store, queue, languages)
        private val reconciler = HaItemReconciler(store, queue, catalog)

        override val isEnabled: Flow<Boolean> = config.config.map { it.enabled && it.isConfigured }.distinctUntilChanged()

        override val isAutoSyncEnabled: Flow<Boolean> =
            config.config.map { it.enabled && it.isConfigured && it.autoSync }.distinctUntilChanged()

        /** One live connection following every linked list; it reconnects when the linked lists change. */
        @OptIn(ExperimentalCoroutinesApi::class)
        override val remoteChanges: Flow<Unit> =
            combine(config.config, store.observeLinkedEntityIds()) { current, entityIds ->
                (current.baseUrl to entityIds).takeIf { current.enabled && current.isConfigured && entityIds.isNotEmpty() }
            }.distinctUntilChanged()
                .flatMapLatest { target ->
                    val credentials = target?.let { config.credentials() }
                    if (target == null || credentials == null) emptyFlow() else liveUpdates.observeItemChanges(credentials, target.second)
                }

        override suspend fun synchronizesNewLists(): Boolean = config.config.first().let { it.enabled && it.isConfigured && it.autoCreateLists }

        override suspend fun synchronize(): SyncOutcome {
            val credentials = config.credentials() ?: return SyncOutcome.Skipped
            val importsAllLists = config.config.first().listMode == HaListMode.ALL_LISTS
            return try {
                // Also catches a list imported by a synchronisation that ran while the mode was left.
                if (!importsAllLists) store.removeImportedLists()
                var failures = sendListDeletions(credentials)
                var unavailable = 0
                val remoteLists = gateway.getTodoLists(credentials).associateBy { it.entityId }
                if (importsAllLists) importMissingLists(remoteLists.values)
                for (list in store.synchronizedLists()) {
                    // Its integration is stopped: nothing can be read, nothing is unlinked or lost.
                    if (list.remoteId?.let(remoteLists::get)?.isAvailable == false) {
                        unavailable++
                        continue
                    }
                    failures +=
                        try {
                            synchronizeList(credentials, list, remoteLists, importsAllLists)
                        } catch (exception: HomeAssistantException) {
                            if (exception.isFatal) throw exception
                            1
                        }
                }
                when {
                    unavailable > 0 -> SyncOutcome.Failure(SyncFailure.LIST_UNAVAILABLE)
                    failures > 0 -> SyncOutcome.Failure(SyncFailure.PROTOCOL)
                    else -> SyncOutcome.Success
                }
            } catch (exception: HomeAssistantException) {
                SyncOutcome.Failure(exception.kind.toSyncFailure())
            }
        }

        /**
         * "All lists" mode: adds the editable Home Assistant lists not linked yet, except those the user
         * removed or unlinked and those whose deletion is still queued.
         */
        private suspend fun importMissingLists(remoteLists: Collection<HaTodoList>) {
            val deleting = queue.pending().filter { it.type == SyncOperationType.DELETE_LIST }.mapNotNull { it.remoteListId }
            val excluded = store.synchronizedLists().mapNotNull { it.remoteId }.toSet() + deleting + store.ignoredEntityIds()
            remoteLists
                .filter { it.isAvailable && it.isEditable && it.entityId !in excluded }
                .forEach { store.importList(it.entityId, it.name) }
        }

        private suspend fun synchronizeList(
            credentials: HaCredentials,
            list: SyncListRef,
            remoteLists: Map<String, HaTodoList>,
            importsAllLists: Boolean,
        ): Int {
            val listOperations = queue.pending().filter { it.listLocalId == list.localId }
            val remoteList = resolveRemoteList(credentials, list, listOperations, remoteLists, importsAllLists) ?: return 0
            if (list.importedFromRemote && list.remoteName != remoteList.name) store.applyRemoteListName(list.localId, remoteList.name)
            queue.complete(listOperations.filter { it.type == SyncOperationType.UPDATE_LIST }.map { it.id })
            val remoteItems = gateway.getItems(credentials, remoteList.entityId)
            reconciler.adoptByName(list.localId, remoteItems)
            val failures = pusher.push(credentials, remoteList, list.localId, remoteItems)
            reconciler.reconcile(list.localId, gateway.getItems(credentials, remoteList.entityId), remoteList.supportsDescription)
            store.markListSynced(list.localId)
            return failures
        }

        private suspend fun resolveRemoteList(
            credentials: HaCredentials,
            list: SyncListRef,
            listOperations: List<SyncOperation>,
            remoteLists: Map<String, HaTodoList>,
            importsAllLists: Boolean,
        ): HaTodoList? {
            if (list.remoteId != null) {
                return remoteLists[list.remoteId] ?: run {
                    if (importsAllLists) store.removeRemotelyDeletedList(list.localId) else store.unlinkList(list.localId)
                    null
                }
            }
            val creation = listOperations.firstOrNull { it.type == SyncOperationType.CREATE_LIST } ?: return null
            // The remote name may differ (« Courses 2 ») when the name is already taken in Home Assistant.
            val created = gateway.createList(credentials, list.name)
            store.setListRemote(list.localId, created.entityId, created.configEntryId, created.name)
            queue.complete(listOf(creation.id))
            return HaTodoList(created.entityId, created.name, supportsDescription = true)
        }

        private suspend fun sendListDeletions(credentials: HaCredentials): Int {
            var failures = 0
            queue.pending().filter { it.type == SyncOperationType.DELETE_LIST }.forEach { operation ->
                try {
                    operation.remoteEntryId?.let { gateway.deleteList(credentials, it) }
                } catch (exception: HomeAssistantException) {
                    if (exception.isFatal) throw exception
                    if (exception.kind != HaErrorKind.NOT_FOUND) {
                        queue.fail(listOf(operation.id), exception.kind.name)
                        failures++
                        return@forEach
                    }
                }
                operation.remoteListId?.let { entityId ->
                    store.forgetTrackedList(entityId)
                    // Created elsewhere, so not deleted remotely: the "all lists" mode must not bring it back.
                    if (operation.remoteEntryId == null) store.ignoreList(entityId)
                }
                queue.complete(listOf(operation.id))
            }
            return failures
        }

        private fun HaErrorKind.toSyncFailure(): SyncFailure =
            when (this) {
                HaErrorKind.UNREACHABLE, HaErrorKind.INVALID_URL -> SyncFailure.UNREACHABLE
                HaErrorKind.UNAUTHORIZED -> SyncFailure.UNAUTHORIZED
                HaErrorKind.NOT_FOUND, HaErrorKind.REJECTED, HaErrorKind.PROTOCOL -> SyncFailure.PROTOCOL
            }
    }
