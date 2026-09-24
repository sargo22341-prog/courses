package org.opensources.courses.testing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.opensources.courses.core.sync.ImportableLists
import org.opensources.courses.core.sync.RemoteChange
import org.opensources.courses.core.sync.RemoteListChoice
import org.opensources.courses.core.sync.RemoteListImport
import org.opensources.courses.core.sync.RemoteSyncEngine
import org.opensources.courses.core.sync.SyncCoordinator
import org.opensources.courses.core.sync.SyncOutcome
import org.opensources.courses.core.sync.SyncQueue
import org.opensources.courses.core.sync.SyncRequest
import org.opensources.courses.feature.homeassistant.domain.HaListLinkRepository

/** `viewModelScope` runs on the test scheduler, eagerly. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}

/** A remote whose synchronisations are counted, and held while [pause] is set. */
class FakeRemoteSyncEngine : RemoteSyncEngine {
    override val isEnabled = MutableStateFlow(true)
    override val isAutoSyncEnabled = MutableStateFlow(true)
    override val remoteChanges = MutableSharedFlow<RemoteChange>()
    var outcome: SyncOutcome = SyncOutcome.Success
    var pause: CompletableDeferred<Unit>? = null
    var synchronizations = 0
        private set

    override suspend fun synchronizesNewLists(): Boolean = false

    override suspend fun synchronize(request: SyncRequest): SyncOutcome {
        synchronizations++
        pause?.await()
        return outcome
    }
}

fun syncCoordinator(
    engine: RemoteSyncEngine,
    scope: CoroutineScope,
    online: Boolean = true,
) = SyncCoordinator(engine, SyncQueue(FakeSyncOperationDao(), fixedClock()), FakeConnectivityObserver(online), scope)

/** Records what the screens asked; the links themselves are tested on Room. */
class FakeHaListLinkRepository : HaListLinkRepository {
    val linked = mutableMapOf<String, String>()
    val created = mutableListOf<String>()
    val unlinked = mutableListOf<String>()
    var unlinkedAll = false
    var importedListsRemoved = false

    override suspend fun linkToExisting(
        listId: String,
        entityId: String,
    ) {
        linked[listId] = entityId
    }

    /** Home Assistant lists imported, by entity id, with the name given. */
    val imported = linkedMapOf<String, String>()

    override suspend fun importList(
        entityId: String,
        remoteName: String,
    ): String {
        imported.putIfAbsent(entityId, remoteName)
        return "imported:$entityId"
    }

    override suspend fun createInHomeAssistant(listId: String) {
        created += listId
    }

    override suspend fun unlink(listId: String) {
        unlinked += listId
    }

    override suspend fun removeImportedLists() {
        importedListsRemoved = true
    }

    override suspend fun unlinkAll() {
        unlinkedAll = true
    }
}

/** Remote lists offered by the test: [result] answers every reading, held while [pause] is set. */
class FakeRemoteListImport : RemoteListImport {
    override val isAvailable = MutableStateFlow(true)
    var result: ImportableLists = ImportableLists.Loaded(emptyList())
    var pause: CompletableDeferred<Unit>? = null
    val imported = mutableListOf<RemoteListChoice>()

    override suspend fun importableLists(): ImportableLists {
        pause?.await()
        return result
    }

    override suspend fun importList(list: RemoteListChoice): String {
        imported += list
        return "imported:${list.remoteId}"
    }
}
