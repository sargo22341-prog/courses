package org.opensources.courses.feature.homeassistant.domain

enum class HaListMode {
    /** Every `todo.*` entity of Home Assistant can be linked. */
    ALL_LISTS,

    /** Only the lists this application created in Home Assistant. */
    APP_CREATED_ONLY,
}

data class HomeAssistantConfig(
    val enabled: Boolean,
    val baseUrl: String,
    val hasToken: Boolean,
    val listMode: HaListMode,
    val autoSync: Boolean,
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && hasToken

    companion object {
        val Default = HomeAssistantConfig(false, "", false, HaListMode.ALL_LISTS, autoSync = true)
    }
}

/** Never logged, never persisted in clear: the token only lives in memory for a request. */
class HaCredentials(
    val baseUrl: String,
    val token: String,
) {
    override fun toString(): String = "HaCredentials(baseUrl=$baseUrl, token=***)"
}
