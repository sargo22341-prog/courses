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

    /**
     * Adds to the app, linked, the Home Assistant list [entityId] under its name [remoteName] (or a
     * free variant, « Courses 2 ») and returns the id of the new list. Its items arrive with the next
     * synchronisation. It is not an "all lists" import: it stays when that mode is left, and deleting
     * it in the app keeps it in Home Assistant, unless this app created it there. A deletion of that
     * list not sent yet is cancelled. A list already linked to [entityId] is returned instead, unchanged.
     */
    suspend fun importList(
        entityId: String,
        remoteName: String,
    ): String

    /** Queues the creation of a Home Assistant list for [listId]. */
    suspend fun createInHomeAssistant(listId: String)

    /**
     * Stops synchronising [listId]; local data is kept and nothing is deleted remotely. The "all
     * lists" mode no longer imports the Home Assistant list it was linked to.
     */
    suspend fun unlink(listId: String)

    /** Removes from this phone the lists imported by the "all lists" mode; they stay in Home Assistant. */
    suspend fun removeImportedLists()

    /**
     * Home Assistant is forgotten: every list stays on this phone, unlinked, and every pending
     * operation is dropped, as is the integration known of each Home Assistant list (another server
     * may use the same entity ids). Lists are not marked as ignored, so connecting again offers them anew.
     */
    suspend fun unlinkAll()
}
