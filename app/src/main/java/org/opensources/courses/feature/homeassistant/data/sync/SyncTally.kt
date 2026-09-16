package org.opensources.courses.feature.homeassistant.data.sync

import org.opensources.courses.core.sync.SyncFailure
import org.opensources.courses.core.sync.SyncOutcome

/** What went wrong during one synchronisation, without stopping it. */
class SyncTally {
    /** Changes refused this time; they stay queued for a later retry. */
    var retried = 0

    /** Changes refused too many times, given up. */
    var abandoned = 0

    /** Linked lists that Home Assistant reports as unavailable; their changes stay queued. */
    var unavailable = 0

    /** Given-up changes come first: the user must learn that they will never reach Home Assistant. */
    fun outcome(): SyncOutcome =
        when {
            abandoned > 0 -> SyncOutcome.Failure(SyncFailure.REJECTED)
            unavailable > 0 -> SyncOutcome.Failure(SyncFailure.LIST_UNAVAILABLE)
            retried > 0 -> SyncOutcome.Failure(SyncFailure.PROTOCOL)
            else -> SyncOutcome.Success
        }
}
