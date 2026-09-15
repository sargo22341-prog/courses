package org.opensources.courses.feature.settings.presentation

import org.opensources.courses.core.sync.SyncSnapshot
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantConfig

/**
 * What the settings entry says about Home Assistant. As on the Home Assistant screen, "connected"
 * means an address and a token are saved: switching synchronisation off does not disconnect it.
 */
sealed interface HomeAssistantStatus {
    data object NotConfigured : HomeAssistantStatus

    /** @property sync the synchronisation state, `null` while synchronisation is switched off. */
    data class Connected(
        val baseUrl: String,
        val sync: SyncSnapshot?,
    ) : HomeAssistantStatus

    companion object {
        fun from(
            config: HomeAssistantConfig,
            sync: SyncSnapshot,
        ): HomeAssistantStatus =
            when {
                !config.isConfigured -> NotConfigured
                config.enabled -> Connected(config.baseUrl, sync)
                else -> Connected(config.baseUrl, sync = null)
            }
    }
}
