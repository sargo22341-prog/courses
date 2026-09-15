package org.opensources.courses.core.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.opensources.courses.core.common.ApplicationScope
import org.opensources.courses.core.network.ConnectivityObserver
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides *when* to synchronise; the [RemoteSyncEngine] decides *how*.
 *
 * Automatic triggers (only when auto-sync is enabled): application start, return to the
 * foreground, a change announced live by the remote while in the foreground, network regained, a
 * new pending operation, and a periodic retry while the process is alive. Pending operations are
 * persisted, so anything not sent before the process dies is sent at the next start.
 * WorkManager is deliberately not used: see README, "Synchronisation".
 */
@Singleton
class SyncCoordinator
    @Inject
    constructor(
        private val engine: RemoteSyncEngine,
        private val queue: SyncQueue,
        private val connectivity: ConnectivityObserver,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        private val running = MutableStateFlow(false)
        private val lastFailure = MutableStateFlow<SyncFailure?>(null)
        private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        private val mutex = Mutex()
        private val started = AtomicBoolean(false)

        // Changed only from the main thread (activity lifecycle).
        private var liveUpdates: Job? = null

        val snapshot: StateFlow<SyncSnapshot> =
            combine(
                connectivity.isOnline,
                running,
                lastFailure,
                engine.isEnabled,
                queue.observePendingCount(),
            ) { online, isRunning, failure, enabled, pending ->
                val state =
                    when {
                        !online -> SyncState.OFFLINE
                        !enabled -> SyncState.ONLINE
                        isRunning -> SyncState.SYNCING
                        failure != null -> SyncState.SYNC_ERROR
                        else -> SyncState.ONLINE
                    }
                SyncSnapshot(state, enabled, pending, failure.takeIf { enabled })
            }.stateIn(scope, SharingStarted.Eagerly, SyncSnapshot.Initial)

        @OptIn(FlowPreview::class)
        fun start() {
            if (!started.compareAndSet(false, true)) return
            scope.launch {
                requests.debounce(REQUEST_DEBOUNCE_MILLIS).collect {
                    if (engine.isAutoSyncEnabled.first()) runSync()
                }
            }
            scope.launch { connectivity.isOnline.filter { it }.collect { requestSync() } }
            scope.launch { queue.observePendingCount().filter { it > 0 }.collect { requestSync() } }
            scope.launch {
                while (isActive) {
                    delay(PERIODIC_SYNC_MILLIS)
                    requestSync()
                }
            }
            requestSync()
        }

        /**
         * The app became visible: catch up with changes made elsewhere meanwhile, then follow the
         * remote live until [onAppBackground].
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun onAppForeground() {
            requestSync()
            if (liveUpdates?.isActive == true) return
            liveUpdates =
                scope.launch {
                    combine(engine.isAutoSyncEnabled, connectivity.isOnline) { autoSync, online -> autoSync && online }
                        .distinctUntilChanged()
                        .flatMapLatest { active -> if (active) engine.remoteChanges else emptyFlow() }
                        .collect { requestSync() }
                }
        }

        /** The live connection is closed; pending operations still leave through the other triggers. */
        fun onAppBackground() {
            liveUpdates?.cancel()
            liveUpdates = null
        }

        /** Asks for an automatic synchronisation; coalesced and ignored when auto-sync is off. */
        fun requestSync() {
            requests.tryEmit(Unit)
        }

        /** Explicit user request ("Synchroniser maintenant"): runs even when auto-sync is off. */
        suspend fun syncNow(): SyncOutcome = runSync()

        private suspend fun runSync(): SyncOutcome =
            mutex.withLock {
                if (!connectivity.isOnline.value) return@withLock SyncOutcome.Offline
                running.value = true
                try {
                    engine.synchronize().also { outcome ->
                        lastFailure.value = (outcome as? SyncOutcome.Failure)?.reason
                    }
                } finally {
                    running.value = false
                }
            }

        private companion object {
            const val REQUEST_DEBOUNCE_MILLIS = 1_500L
            const val PERIODIC_SYNC_MILLIS = 2 * 60 * 1_000L
        }
    }
