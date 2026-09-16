package org.opensources.courses.core.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
 * foreground, network regained, a new pending operation and, while the app is in the foreground
 * only, a change announced live by the remote and a periodic retry. Pending operations are
 * persisted, so anything not sent before the process dies is sent at the next start.
 * WorkManager is deliberately not used: see docs/adr/0004-pas-de-workmanager.md and
 * docs/adr/0023-synchronisation-periodique-au-premier-plan.md.
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
        private var foregroundWork: Job? = null

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
            // Subscribed before returning: a request emitted with no subscriber yet would be lost.
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                requests.debounce(REQUEST_DEBOUNCE_MILLIS).collect {
                    if (engine.isAutoSyncEnabled.first()) runSync()
                }
            }
            scope.launch { connectivity.isOnline.filter { it }.collect { requestSync() } }
            scope.launch { queue.observePendingCount().filter { it > 0 }.collect { requestSync() } }
            requestSync()
        }

        /**
         * The app became visible: catch up with changes made elsewhere meanwhile, then follow the
         * remote live and retry periodically until [onAppBackground].
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        fun onAppForeground() {
            requestSync()
            if (foregroundWork?.isActive == true) return
            foregroundWork =
                scope.launch {
                    launch {
                        while (isActive) {
                            delay(PERIODIC_SYNC_MILLIS)
                            requestSync()
                        }
                    }
                    combine(engine.isAutoSyncEnabled, connectivity.isOnline) { autoSync, online -> autoSync && online }
                        .distinctUntilChanged()
                        .flatMapLatest { active -> if (active) engine.remoteChanges else emptyFlow() }
                        .collect { requestSync() }
                }
        }

        /**
         * Nobody looks at the lists: the live connection and the periodic retry stop, so the radio is
         * not woken up every few minutes. New pending operations and a regained network still trigger
         * a synchronisation.
         */
        fun onAppBackground() {
            foregroundWork?.cancel()
            foregroundWork = null
        }

        /** Asks for an automatic synchronisation; coalesced and ignored when auto-sync is off. */
        fun requestSync() {
            requests.tryEmit(Unit)
        }

        /** Explicit user request ("Synchroniser maintenant"): runs even when auto-sync is off. */
        suspend fun syncNow(): SyncOutcome = runSync()

        /**
         * Runs [block] once no synchronisation is running, and keeps any from starting until it ends:
         * a synchronisation must not write remote data into what [block] resets.
         */
        suspend fun <T> withoutSynchronisation(block: suspend () -> T): T = mutex.withLock { block() }

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
