package org.opensources.courses.core.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.opensources.courses.testing.FakeConnectivityObserver
import org.opensources.courses.testing.FakeSyncOperationDao
import org.opensources.courses.testing.fixedClock

@OptIn(ExperimentalCoroutinesApi::class)
class SyncCoordinatorTest {
    private class RecordingEngine : RemoteSyncEngine {
        override val isEnabled = MutableStateFlow(true)
        override val isAutoSyncEnabled = MutableStateFlow(true)
        val changes = MutableSharedFlow<RemoteChange>()
        override val remoteChanges = changes
        val requests = mutableListOf<SyncRequest>()
        val synchronizations get() = requests.size

        override suspend fun synchronizesNewLists(): Boolean = false

        override suspend fun synchronize(request: SyncRequest): SyncOutcome {
            requests += request
            return SyncOutcome.Success
        }
    }

    private val engine = RecordingEngine()
    private val dao = FakeSyncOperationDao()
    private val queue = SyncQueue(dao, fixedClock())

    private fun TestScope.coordinator() = SyncCoordinator(engine, queue, FakeConnectivityObserver(online = true), backgroundScope)

    private fun TestScope.settle() = advanceTimeBy(DEBOUNCE_ELAPSED_MILLIS)

    /** Started and visible, with the synchronisations of both already done. */
    private fun TestScope.visibleCoordinator(): SyncCoordinator =
        coordinator().apply {
            start()
            settle()
            onAppForeground()
            settle()
        }

    @Test
    fun `opening the app synchronises, then remote changes do while it stays visible`() =
        runTest {
            val coordinator = coordinator().apply { start() }
            settle()
            val atStart = engine.synchronizations

            coordinator.onAppForeground()
            settle()
            assertEquals(atStart + 1, engine.synchronizations)

            engine.changes.emit(RemoteChange.ItemsChanged(setOf(LIST_A)))
            settle()
            assertEquals(atStart + 2, engine.synchronizations)

            coordinator.onAppBackground()
            settle()
            engine.changes.emit(RemoteChange.ItemsChanged(setOf(LIST_A)))
            settle()
            assertEquals(atStart + 2, engine.synchronizations)
        }

    @Test
    fun `start and return to the app read everything, a live change only its list`() =
        runTest {
            visibleCoordinator()

            engine.changes.emit(RemoteChange.ItemsChanged(setOf(LIST_A)))
            settle()

            assertEquals(listOf(SyncRequest.Full, SyncRequest.Full, SyncRequest.remoteItems(setOf(LIST_A))), engine.requests)
        }

    @Test
    fun `a local change is sent without reading the lists again`() =
        runTest {
            coordinator().start()
            settle()

            queue.enqueue(SyncOperationType.CHECK_ITEM, "list", "item")
            settle()

            assertEquals(SyncRequest.LocalChanges, engine.requests.last())
        }

    @Test
    fun `requests made while waiting are merged into one synchronisation`() =
        runTest {
            val coordinator = visibleCoordinator()
            val before = engine.synchronizations

            engine.changes.emit(RemoteChange.ItemsChanged(setOf(LIST_A)))
            engine.changes.emit(RemoteChange.ItemsChanged(setOf(LIST_B)))
            settle()
            engine.changes.emit(RemoteChange.ItemsChanged(setOf(LIST_A)))
            coordinator.requestSync()
            settle()

            assertEquals(before + 2, engine.synchronizations)
            assertEquals(listOf(SyncRequest.remoteItems(setOf(LIST_A, LIST_B)), SyncRequest.Full), engine.requests.takeLast(2))
        }

    @Test
    fun `the periodic synchronisation runs only while the app is visible`() =
        runTest {
            val coordinator = coordinator().apply { start() }
            settle()
            advanceTimeBy(TEN_MINUTES_MILLIS)
            val hidden = engine.synchronizations

            coordinator.onAppForeground()
            settle()
            val visible = engine.synchronizations
            advanceTimeBy(PERIODIC_ELAPSED_MILLIS)
            assertEquals(visible + 1, engine.synchronizations)
            assertEquals(SyncRequest.Full, engine.requests.last())

            coordinator.onAppBackground()
            val backgrounded = engine.synchronizations
            advanceTimeBy(TEN_MINUTES_MILLIS)

            assertEquals(1, hidden)
            assertEquals(backgrounded, engine.synchronizations)
        }

    @Test
    fun `while changes are followed live, the periodic synchronisation runs every 10 minutes`() =
        runTest {
            visibleCoordinator()
            engine.changes.emit(RemoteChange.ItemsChanged(setOf(LIST_A)))
            settle()
            val live = engine.synchronizations

            advanceTimeBy(EIGHT_MINUTES_MILLIS)
            assertEquals(live, engine.synchronizations)
            advanceTimeBy(PERIODIC_ELAPSED_MILLIS)
            assertEquals(live + 1, engine.synchronizations)
            assertEquals(SyncRequest.Full, engine.requests.last())
        }

    @Test
    fun `once the live connection is lost, the periodic synchronisation runs every 2 minutes again`() =
        runTest {
            visibleCoordinator()
            engine.changes.emit(RemoteChange.ItemsChanged(setOf(LIST_A)))
            settle()
            engine.changes.emit(RemoteChange.Disconnected)
            val lost = engine.synchronizations

            advanceTimeBy(PERIODIC_ELAPSED_MILLIS)

            assertEquals(lost + 1, engine.synchronizations)
        }

    @Test
    fun `a refused live connection asks for a synchronisation that reports why`() =
        runTest {
            visibleCoordinator()
            val before = engine.synchronizations

            engine.changes.emit(RemoteChange.Refused)
            settle()

            assertEquals(before + 1, engine.synchronizations)
            assertEquals(SyncRequest.Full, engine.requests.last())
        }

    @Test
    fun `the pending operations are counted by a single query`() =
        runTest {
            val coordinator = coordinator().apply { start() }
            settle()

            coordinator.onAppForeground()
            settle()

            assertEquals(1, dao.countObservers)
        }

    @Test
    fun `a synchronisation asked meanwhile waits for the exclusive work to end`() =
        runTest {
            val coordinator = coordinator().apply { start() }
            settle()
            val before = engine.synchronizations

            coordinator.withoutSynchronisation {
                coordinator.requestSync()
                settle()
                assertEquals(before, engine.synchronizations)
            }
            settle()

            assertEquals(before + 1, engine.synchronizations)
        }

    private companion object {
        const val LIST_A = "todo.a"
        const val LIST_B = "todo.b"
        const val DEBOUNCE_ELAPSED_MILLIS = 2_000L
        const val PERIODIC_ELAPSED_MILLIS = 2 * 60 * 1_000L + DEBOUNCE_ELAPSED_MILLIS
        const val EIGHT_MINUTES_MILLIS = 8 * 60 * 1_000L
        const val TEN_MINUTES_MILLIS = 10 * 60 * 1_000L
    }
}
