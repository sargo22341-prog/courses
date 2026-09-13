package org.opensources.courses.core.sync

import kotlinx.coroutines.flow.Flow

/**
 * A remote the local database can be synchronised with. Home Assistant is the only
 * implementation; `core` does not know any of its details.
 */
interface RemoteSyncEngine {
    /** True when the remote is configured and enabled by the user. */
    val isEnabled: Flow<Boolean>

    /** True when changes should be pushed/pulled without an explicit user action. */
    val isAutoSyncEnabled: Flow<Boolean>

    /** True when a list created now must be synchronised from the start (created remotely at the next sync). */
    suspend fun synchronizesNewLists(): Boolean

    suspend fun synchronize(): SyncOutcome
}

sealed interface SyncOutcome {
    data object Success : SyncOutcome

    /** Nothing to do: remote disabled or not configured. */
    data object Skipped : SyncOutcome

    data object Offline : SyncOutcome

    data class Failure(
        val reason: SyncFailure,
    ) : SyncOutcome
}

enum class SyncFailure {
    UNREACHABLE,
    UNAUTHORIZED,
    PROTOCOL,
}
