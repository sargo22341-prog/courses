package org.opensources.courses.feature.homeassistant.domain

enum class HaListMode {
    /** Every `todo.*` entity of Home Assistant can be linked. */
    ALL_LISTS,

    /** Only the lists this application created in Home Assistant. */
    APP_CREATED_ONLY,
}

/**
 * @property autoCreateLists a list created in the app is created in Home Assistant automatically;
 * when off, the user links it by hand.
 * @property listsSetupDone the user was already asked what to do with the lists that existed before
 * Home Assistant was set up.
 */
data class HomeAssistantConfig(
    val enabled: Boolean,
    val baseUrl: String,
    val hasToken: Boolean,
    val listMode: HaListMode,
    val autoSync: Boolean,
    val autoCreateLists: Boolean = true,
    val listsSetupDone: Boolean = false,
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
