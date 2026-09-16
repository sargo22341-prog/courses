package org.opensources.courses.feature.homeassistant.domain

import kotlinx.coroutines.flow.Flow
import org.opensources.courses.core.sync.RemoteChange

/** Changes made to Home Assistant to-do lists, signalled while a live connection is open. */
interface HaLiveUpdates {
    /**
     * Emits [RemoteChange.ItemsChanged] with the entity id when an item of one of [entityIds] changes,
     * and for each of them once per connection (the current items are sent on subscription). Never
     * fails: a lost connection emits [RemoteChange.Disconnected] and is reopened by itself. When
     * retrying cannot help (token refused, invalid address), it emits [RemoteChange.Refused] and
     * completes, so that the synchronisation it asks for reports the problem.
     */
    fun observeItemChanges(
        credentials: HaCredentials,
        entityIds: Set<String>,
    ): Flow<RemoteChange>
}
