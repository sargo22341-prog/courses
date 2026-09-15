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
    private class CountingEngine : RemoteSyncEngine {
        override val isEnabled = MutableStateFlow(true)
        override val isAutoSyncEnabled = MutableStateFlow(true)
        val changes = MutableSharedFlow<Unit>()
        override val remoteChanges = changes
        var synchronizations = 0

        override suspend fun synchronizesNewLists(): Boolean = false

        override suspend fun synchronize(): SyncOutcome {
            synchronizations++
            return SyncOutcome.Success
        }
    }

    private val engine = CountingEngine()

    private fun TestScope.coordinator() =
        SyncCoordinator(engine, SyncQueue(FakeSyncOperationDao(), fixedClock()), FakeConnectivityObserver(online = true), backgroundScope)

    private fun TestScope.settle() = advanceTimeBy(DEBOUNCE_ELAPSED_MILLIS)

    @Test
    fun `opening the app synchronises, then remote changes do while it stays visible`() =
        runTest {
            val coordinator = coordinator().apply { start() }
            settle()
            val atStart = engine.synchronizations

            coordinator.onAppForeground()
            settle()
            assertEquals(atStart + 1, engine.synchronizations)

            engine.changes.emit(Unit)
            settle()
            assertEquals(atStart + 2, engine.synchronizations)

            coordinator.onAppBackground()
            settle()
            engine.changes.emit(Unit)
            settle()
            assertEquals(atStart + 2, engine.synchronizations)
        }

    private companion object {
        const val DEBOUNCE_ELAPSED_MILLIS = 2_000L
    }
}
