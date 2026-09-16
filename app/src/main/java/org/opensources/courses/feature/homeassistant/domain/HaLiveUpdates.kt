package org.opensources.courses.feature.homeassistant.domain

import kotlinx.coroutines.flow.Flow

/** Changes made to Home Assistant to-do lists, signalled while a live connection is open. */
interface HaLiveUpdates {
    /**
     * Emits when an item of one of [entityIds] changes, and once per connection (the current items
     * are sent on subscription). Never fails: a lost connection is reopened by itself. When retrying
     * cannot help (token refused, invalid address), it emits once more and completes, so that the
     * synchronisation it asks for reports the problem.
     */
    fun observeItemChanges(
        credentials: HaCredentials,
        entityIds: Set<String>,
    ): Flow<Unit>
}
