package org.opensources.courses.feature.settings.presentation

import org.junit.Assert.assertEquals
import org.junit.Test
import org.opensources.courses.core.sync.SyncSnapshot
import org.opensources.courses.core.sync.SyncState
import org.opensources.courses.feature.homeassistant.domain.HaListMode
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantConfig

class HomeAssistantStatusTest {
    private val sync = SyncSnapshot(SyncState.ONLINE, remoteEnabled = true, pendingCount = 2, failure = null)

    private fun config(
        enabled: Boolean,
        baseUrl: String = URL,
        hasToken: Boolean = true,
    ) = HomeAssistantConfig(enabled, baseUrl, hasToken, HaListMode.ALL_LISTS, autoSync = true)

    @Test
    fun `a saved connection with synchronisation switched off is still connected`() {
        assertEquals(HomeAssistantStatus.Connected(URL, sync = null), HomeAssistantStatus.from(config(enabled = false), sync))
    }

    @Test
    fun `a connection with synchronisation on carries the synchronisation state`() {
        assertEquals(HomeAssistantStatus.Connected(URL, sync), HomeAssistantStatus.from(config(enabled = true), sync))
    }

    @Test
    fun `without an address or a token Home Assistant is not configured`() {
        assertEquals(HomeAssistantStatus.NotConfigured, HomeAssistantStatus.from(config(enabled = true, hasToken = false), sync))
        assertEquals(HomeAssistantStatus.NotConfigured, HomeAssistantStatus.from(config(enabled = false, baseUrl = ""), sync))
    }

    private companion object {
        const val URL = "http://ha.local:8123"
    }
}
