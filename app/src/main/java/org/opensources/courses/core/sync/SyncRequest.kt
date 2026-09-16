package org.opensources.courses.core.sync

/**
 * What a synchronisation must look at.
 *
 * @property refreshLists read the remote lists again. Otherwise the engine may reuse the lists read by
 * its last successful synchronisation: items changing does not change the lists.
 * @property remoteListIds only the synchronised lists with these remote ids; null for all of them.
 */
data class SyncRequest(
    val refreshLists: Boolean,
    val remoteListIds: Set<String>? = null,
) {
    /** One synchronisation doing what both requests ask. */
    operator fun plus(other: SyncRequest): SyncRequest =
        SyncRequest(
            refreshLists = refreshLists || other.refreshLists,
            remoteListIds = if (remoteListIds == null || other.remoteListIds == null) null else remoteListIds + other.remoteListIds,
        )

    companion object {
        /** Everything, lists included: start, return to the app, network regained, retry, user request. */
        val Full = SyncRequest(refreshLists = true)

        /** Local changes to send, in any list. */
        val LocalChanges = SyncRequest(refreshLists = false)

        /** Items announced live as changed in these remote lists. */
        fun remoteItems(remoteListIds: Set<String>) = SyncRequest(refreshLists = false, remoteListIds = remoteListIds)
    }
}

/** What a remote announces while it is followed live. */
sealed interface RemoteChange {
    /** Items of these remote lists changed; also sent for every followed list when the connection opens. */
    data class ItemsChanged(
        val remoteListIds: Set<String>,
    ) : RemoteChange

    /** Nothing is followed live (connection lost and being opened again, or nothing to follow). */
    data object Disconnected : RemoteChange

    /** Following live cannot work (token refused, invalid address): a synchronisation reports why. */
    data object Refused : RemoteChange
}
