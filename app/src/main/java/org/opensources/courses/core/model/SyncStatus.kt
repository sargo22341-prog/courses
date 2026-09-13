package org.opensources.courses.core.model

/**
 * Synchronisation state of a list or an item.
 *
 * - [LOCAL_ONLY]: the object belongs to a list that is not linked to Home Assistant.
 * - [PENDING]: a local change has not been confirmed by Home Assistant yet. Remote data never
 *   overwrites an object in this state (see `ConflictResolver`).
 * - [SYNCED]: local and remote were equal at the end of the last synchronisation.
 */
enum class SyncStatus {
    LOCAL_ONLY,
    PENDING,
    SYNCED,
}
