package org.opensources.courses.feature.catalog.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CatalogFreshnessPolicyTest {
    private val policy = CatalogFreshnessPolicy()
    private val now = Instant.parse("2026-09-13T10:00:00Z")

    @Test
    fun `never synchronised catalog is stale`() {
        assertTrue(policy.isStale(null, now))
    }

    @Test
    fun `catalog from twelve days ago is stale`() {
        assertTrue(policy.isStale(Instant.parse("2026-09-01T10:00:00Z"), now))
    }

    @Test
    fun `catalog exactly one week old is stale`() {
        assertTrue(policy.isStale(Instant.parse("2026-09-06T10:00:00Z"), now))
    }

    @Test
    fun `catalog from three days ago is fresh`() {
        assertFalse(policy.isStale(Instant.parse("2026-09-10T10:00:00Z"), now))
    }

    @Test
    fun `sync date in the future is treated as stale`() {
        assertTrue(policy.isStale(Instant.parse("2026-10-01T10:00:00Z"), now))
    }
}
