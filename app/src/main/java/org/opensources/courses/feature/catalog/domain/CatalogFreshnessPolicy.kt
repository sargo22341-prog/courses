package org.opensources.courses.feature.catalog.domain

import java.time.Duration
import java.time.Instant

/** The catalog is refreshed when it is older than [maxAge] (one week by default). */
class CatalogFreshnessPolicy(
    private val maxAge: Duration = Duration.ofDays(7),
) {
    fun isStale(
        lastSyncAt: Instant?,
        now: Instant,
    ): Boolean {
        if (lastSyncAt == null) return true
        // A date in the future means the device clock moved backwards: refresh rather than wait.
        if (lastSyncAt.isAfter(now)) return true
        return Duration.between(lastSyncAt, now) >= maxAge
    }
}
