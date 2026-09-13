package org.opensources.courses.feature.homeassistant.domain

import kotlinx.coroutines.flow.Flow

/** Links local lists to Home Assistant lists. All operations are local and work offline. */
interface HaListLinkRepository {
    /** Entity ids of the lists this application created in Home Assistant. */
    fun observeTrackedEntityIds(): Flow<Set<String>>

    /** Links [listId] to an existing Home Assistant list; items are merged by name at the next sync. */
    suspend fun linkToExisting(
        listId: String,
        entityId: String,
    )

    /** Queues the creation of a Home Assistant list for [listId]. */
    suspend fun createInHomeAssistant(listId: String)

    /** Stops synchronising [listId]; local data is kept and nothing is deleted remotely. */
    suspend fun unlink(listId: String)
}
