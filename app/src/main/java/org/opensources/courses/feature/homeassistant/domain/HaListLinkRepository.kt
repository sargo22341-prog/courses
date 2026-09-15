package org.opensources.courses.feature.homeassistant.domain

/** Links local lists to Home Assistant lists. All operations are local and work offline. */
interface HaListLinkRepository {
    /**
     * Links [listId] to an existing Home Assistant list; items are merged by name at the next sync.
     * A copy imported from that list is removed (only unlinked if it holds unsent changes); another
     * list of the user linked to it is unlinked.
     */
    suspend fun linkToExisting(
        listId: String,
        entityId: String,
    )

    /** Queues the creation of a Home Assistant list for [listId]. */
    suspend fun createInHomeAssistant(listId: String)

    /**
     * Stops synchronising [listId]; local data is kept and nothing is deleted remotely. The "all
     * lists" mode no longer imports the Home Assistant list it was linked to.
     */
    suspend fun unlink(listId: String)

    /** Removes from this phone the lists imported by the "all lists" mode; they stay in Home Assistant. */
    suspend fun removeImportedLists()
}
