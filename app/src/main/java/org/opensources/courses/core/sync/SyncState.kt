package org.opensources.courses.core.sync

enum class SyncState {
    ONLINE,
    OFFLINE,
    SYNCING,
    SYNC_ERROR,
}

data class SyncSnapshot(
    val state: SyncState,
    val remoteEnabled: Boolean,
    val pendingCount: Int,
    val failure: SyncFailure?,
) {
    companion object {
        val Initial = SyncSnapshot(SyncState.ONLINE, remoteEnabled = false, pendingCount = 0, failure = null)
    }
}
