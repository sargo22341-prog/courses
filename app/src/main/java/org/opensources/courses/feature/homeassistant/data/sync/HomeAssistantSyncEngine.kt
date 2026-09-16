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
import javax.inject.Singleton

/**
 * Bidirectional synchronisation with Home Assistant to-do lists.
 *
 * The Home Assistant lists are read first, which also checks the token before anything is counted
 * as refused. The pending operations are read once: those queued meanwhile leave at the next
 * synchronisation, which their insertion requests anyway.
 *
 * Lists come next: deletions of lists removed in the app are sent. In "all lists" mode, every
 * editable Home Assistant list missing from the app is then imported, and a list deleted in Home
 * Assistant leaves the app; in "lists created by the app" mode, lists imported earlier are removed
 * and a list deleted in Home Assistant is only unlinked.
 *
 * Then, for each synchronised list:
 * 1. create the remote list if a `CREATE_LIST` operation is pending;
 * 2. read remote items and give local items without uid the uid of a same-named remote item;
 * 3. push pending item operations ([HaItemPusher]);
 * 4. apply the remote items ([HaItemReconciler], [ConflictResolver] rules), read again if
 *    something was pushed.
 *
 * `UPDATE_LIST` (rename) cannot be sent: the Home Assistant REST API offers no way to rename a to-do
 * entity, so the new name stays local. A rename made in Home Assistant renames imported lists.
 * A linked list that is unavailable in Home Assistant is skipped: its changes stay queued. An
 * operation refused [SyncQueue.MAX_ATTEMPTS] times is given up (docs/adr/0022-abandon-des-operations-refusees.md).
 */
@Singleton
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

        /**
         * One live connection following every linked list. It is opened again when the linked lists,
         * the address or the token change: a connection ended by a refused token resumes once the
         * user saved another one.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        override val remoteChanges: Flow<Unit> =
            combine(config.config, store.observeLinkedEntityIds()) { current, entityIds ->
                LiveTarget(current.baseUrl, current.tokenVersion, entityIds).takeIf { current.enabled && current.isConfigured && entityIds.isNotEmpty() }
            }.distinctUntilChanged()
                .flatMapLatest { target ->
                    val credentials = target?.let { config.credentials() }
                    if (target == null || credentials == null) emptyFlow() else liveUpdates.observeItemChanges(credentials, target.entityIds)
                }

        override suspend fun synchronizesNewLists(): Boolean = config.config.first().let { it.enabled && it.isConfigured && it.autoCreateLists }

        override suspend fun synchronize(): SyncOutcome {
            val credentials = config.credentials() ?: return SyncOutcome.Skipped
            val importsAllLists = config.config.first().listMode == HaListMode.ALL_LISTS
            val tally = SyncTally()
            return try {
                // Also catches a list imported by a synchronisation that ran while the mode was left.
                if (!importsAllLists) store.removeImportedLists()
                val remoteLists = gateway.getTodoLists(credentials).associateBy { it.entityId }
                val pending = queue.pending()
                sendListDeletions(credentials, pending.filter { it.type == SyncOperationType.DELETE_LIST }, tally)
                if (importsAllLists) importMissingLists(remoteLists.values, pending)
                val pendingByList = pending.groupBy { it.listLocalId }
                for (list in store.synchronizedLists()) {
                    // Its integration is stopped: nothing can be read, nothing is unlinked or lost.
                    if (list.remoteId?.let(remoteLists::get)?.isAvailable == false) {
                        tally.unavailable++
                        continue
                    }
                    try {
                        synchronizeList(credentials, list, pendingByList[list.localId].orEmpty(), remoteLists, importsAllLists, tally)
                    } catch (exception: HomeAssistantException) {
                        // A list that cannot be read is retried as a whole at the next synchronisation.
                        if (exception.isFatal) throw exception
                        tally.retried++
                    }
                }
                tally.outcome()
            } catch (exception: HomeAssistantException) {
                SyncOutcome.Failure(exception.kind.toSyncFailure())
            }
        }

        /**
         * "All lists" mode: adds the editable Home Assistant lists not linked yet, except those the user
         * removed or unlinked and those whose deletion was queued.
         */
        private suspend fun importMissingLists(
            remoteLists: Collection<HaTodoList>,
            pending: List<SyncOperation>,
        ) {
            val deleting = pending.filter { it.type == SyncOperationType.DELETE_LIST }.mapNotNull { it.remoteListId }
            val excluded = store.synchronizedLists().mapNotNull { it.remoteId }.toSet() + deleting + store.ignoredEntityIds()
            remoteLists
                .filter { it.isAvailable && it.isEditable && it.entityId !in excluded }
                .forEach { store.importList(it.entityId, it.name) }
        }

        private suspend fun synchronizeList(
            credentials: HaCredentials,
            list: SyncListRef,
            listOperations: List<SyncOperation>,
            remoteLists: Map<String, HaTodoList>,
            importsAllLists: Boolean,
            tally: SyncTally,
        ) {
            val remoteList = resolveRemoteList(credentials, list, listOperations, remoteLists, importsAllLists, tally) ?: return
            if (list.importedFromRemote && list.remoteName != remoteList.name) store.applyRemoteListName(list.localId, remoteList.name)
            queue.complete(listOperations.filter { it.type == SyncOperationType.UPDATE_LIST }.map { it.id })
            val itemOperations = listOperations.filter { it.type.isItemOperation }
            val remoteItems = gateway.getItems(credentials, remoteList.entityId)
            reconciler.adoptByName(list.localId, remoteItems)
            pusher.push(credentials, remoteList, list.localId, itemOperations, remoteItems, tally)
            // Nothing sent: what was read is still what Home Assistant holds.
            val latest = if (itemOperations.isEmpty()) remoteItems else gateway.getItems(credentials, remoteList.entityId)
            reconciler.reconcile(list.localId, latest, remoteList.supportsDescription)
            store.markListSynced(list.localId)
        }

        private suspend fun resolveRemoteList(
            credentials: HaCredentials,
            list: SyncListRef,
            listOperations: List<SyncOperation>,
            remoteLists: Map<String, HaTodoList>,
            importsAllLists: Boolean,
            tally: SyncTally,
        ): HaTodoList? {
            if (list.remoteId != null) {
                return remoteLists[list.remoteId] ?: run {
                    if (importsAllLists) store.removeRemotelyDeletedList(list.localId) else store.unlinkList(list.localId)
                    null
                }
            }
            val creation = listOperations.firstOrNull { it.type == SyncOperationType.CREATE_LIST } ?: return null
            val created =
                try {
                    // The remote name may differ (« Courses 2 ») when the name is already taken in Home Assistant.
                    gateway.createList(credentials, list.name)
                } catch (exception: HomeAssistantException) {
                    if (exception.isFatal) throw exception
                    if (creation.isLastAttempt) {
                        // Given up: the list stays on this phone, the user can link it by hand.
                        store.unlinkList(list.localId)
                        tally.abandoned++
                    } else {
                        queue.fail(listOf(creation.id), exception.kind.name)
                        tally.retried++
                    }
                    return null
                }
            store.setListRemote(list.localId, created.entityId, created.configEntryId, created.name)
            queue.complete(listOf(creation.id))
            return HaTodoList(created.entityId, created.name, supportsDescription = true)
        }

        private suspend fun sendListDeletions(
            credentials: HaCredentials,
            deletions: List<SyncOperation>,
            tally: SyncTally,
        ) {
            for (operation in deletions) {
                var abandoned = false
                try {
                    operation.remoteEntryId?.let { gateway.deleteList(credentials, it) }
                } catch (exception: HomeAssistantException) {
                    if (exception.isFatal) throw exception
                    if (exception.kind != HaErrorKind.NOT_FOUND) {
                        if (!operation.isLastAttempt) {
                            queue.fail(listOf(operation.id), exception.kind.name)
                            tally.retried++
                            continue
                        }
                        abandoned = true
                        tally.abandoned++
                    }
                }
                operation.remoteListId?.let { entityId ->
                    store.forgetTrackedList(entityId)
                    // Not deleted remotely (created elsewhere, or refused): the "all lists" mode must not bring it back.
                    if (operation.remoteEntryId == null || abandoned) store.ignoreList(entityId)
                }
                queue.complete(listOf(operation.id))
            }
        }

        private fun HaErrorKind.toSyncFailure(): SyncFailure =
            when (this) {
                HaErrorKind.UNREACHABLE, HaErrorKind.INVALID_URL -> SyncFailure.UNREACHABLE
                HaErrorKind.UNAUTHORIZED -> SyncFailure.UNAUTHORIZED
                HaErrorKind.NOT_FOUND, HaErrorKind.REJECTED, HaErrorKind.PROTOCOL -> SyncFailure.PROTOCOL
            }

        /** What the live connection depends on; the token itself is read again when it is opened. */
        private data class LiveTarget(
            val baseUrl: String,
            val tokenVersion: Int,
            val entityIds: Set<String>,
        )
    }
