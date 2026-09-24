package org.opensources.courses.core.sync

import kotlinx.coroutines.flow.Flow

/**
 * Lists of the remote that the user brings into the app one at a time, instead of creating an empty
 * list. Home Assistant is the only implementation; `core` does not know any of its details.
 */
interface RemoteListImport {
    /**
     * True when a list can be imported: the remote is enabled and configured, and does not already
     * bring all its lists into the app by itself.
     */
    val isAvailable: Flow<Boolean>

    /** The remote lists that can be imported now: usable, editable and not linked to a list of the app. */
    suspend fun importableLists(): ImportableLists

    /**
     * Adds [list] to the app, linked to the remote, and returns the id of the new list; its items
     * arrive with the synchronisation requested at once. A list already linked to it is returned instead.
     */
    suspend fun importList(list: RemoteListChoice): String
}

data class RemoteListChoice(
    val remoteId: String,
    val name: String,
)

sealed interface ImportableLists {
    data class Loaded(
        val lists: List<RemoteListChoice>,
    ) : ImportableLists

    /** The remote could not tell; the lists of the app are untouched. */
    data class Failed(
        val reason: SyncFailure,
    ) : ImportableLists
}
