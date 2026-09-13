package org.opensources.courses.feature.homeassistant.data.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.opensources.courses.core.sync.RemoteSyncEngine
import org.opensources.courses.core.sync.SyncFailure
import org.opensources.courses.core.sync.SyncOperation
import org.opensources.courses.core.sync.SyncOperationType
import org.opensources.courses.core.sync.SyncOutcome
import org.opensources.courses.core.sync.SyncQueue
import org.opensources.courses.feature.homeassistant.domain.HaConfigRepository
import org.opensources.courses.feature.homeassistant.domain.HaCredentials
import org.opensources.courses.feature.homeassistant.domain.HaErrorKind
import org.opensources.courses.feature.homeassistant.domain.HaTodoList
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantException
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantGateway
import javax.inject.Inject

/**
 * Bidirectional synchronisation with Home Assistant to-do lists.
 *
 * For each synchronised list:
 * 1. create the remote list if a `CREATE_LIST` operation is pending;
 * 2. read remote items and give local items without uid the uid of a same-named remote item;
 * 3. push pending item operations ([HaItemPusher]);
 * 4. read remote items again and apply them ([HaItemReconciler], [ConflictResolver] rules).
 *
 * Deletions of lists created by the app are sent first. `UPDATE_LIST` (rename) cannot be sent:
 * the Home Assistant REST API offers no way to rename a to-do entity, so the new name stays local.
 */
class HomeAssistantSyncEngine
    @Inject
    constructor(
        private val config: HaConfigRepository,
        private val gateway: HomeAssistantGateway,
        private val store: SyncLocalStore,
        private val queue: SyncQueue,
    ) : RemoteSyncEngine {
        private val pusher = HaItemPusher(gateway, store, queue)
        private val reconciler = HaItemReconciler(store, queue)

        override val isEnabled: Flow<Boolean> = config.config.map { it.enabled && it.isConfigured }.distinctUntilChanged()

        override val isAutoSyncEnabled: Flow<Boolean> =
            config.config.map { it.enabled && it.isConfigured && it.autoSync }.distinctUntilChanged()

        override suspend fun synchronize(): SyncOutcome {
            val credentials = config.credentials() ?: return SyncOutcome.Skipped
            return try {
                var failures = sendListDeletions(credentials)
                val remoteLists = gateway.getTodoLists(credentials).associateBy { it.entityId }
                for (list in store.synchronizedLists()) {
                    failures +=
                        try {
                            synchronizeList(credentials, list, remoteLists)
                        } catch (exception: HomeAssistantException) {
                            if (exception.isFatal) throw exception
                            1
                        }
                }
                if (failures == 0) SyncOutcome.Success else SyncOutcome.Failure(SyncFailure.PROTOCOL)
            } catch (exception: HomeAssistantException) {
                SyncOutcome.Failure(exception.kind.toSyncFailure())
            }
        }

        private suspend fun synchronizeList(
            credentials: HaCredentials,
            list: SyncListRef,
            remoteLists: Map<String, HaTodoList>,
        ): Int {
            val listOperations = queue.pending().filter { it.listLocalId == list.localId }
            val remoteList = resolveRemoteList(credentials, list, listOperations, remoteLists) ?: return 0
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
        ): HaTodoList? {
            if (list.remoteId != null) {
                return remoteLists[list.remoteId] ?: run {
                    store.unlinkList(list.localId)
                    null
                }
            }
            val creation = listOperations.firstOrNull { it.type == SyncOperationType.CREATE_LIST } ?: return null
            val created = gateway.createList(credentials, list.name)
            store.setListRemote(list.localId, created.entityId, created.configEntryId, list.name)
            queue.complete(listOf(creation.id))
            return HaTodoList(created.entityId, list.name, supportsDescription = true)
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
                operation.remoteListId?.let { store.forgetTrackedList(it) }
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
