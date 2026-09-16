package org.opensources.courses.feature.homeassistant.domain

import kotlinx.coroutines.flow.Flow

interface HaConfigRepository {
    val config: Flow<HomeAssistantConfig>

    /** Credentials to use for synchronisation, or null when Home Assistant is disabled or incomplete. */
    suspend fun credentials(): HaCredentials?

    /** Credentials for a connection test: the typed token, or the stored one when [typedToken] is blank. */
    suspend fun credentialsFor(
        baseUrl: String,
        typedToken: String,
    ): HaCredentials?

    /**
     * Saves the address and, when not blank, replaces the stored token. Returns false, and saves
     * nothing, when [baseUrl] is not an http(s) address.
     */
    suspend fun saveConnection(
        baseUrl: String,
        token: String,
    ): Boolean

    /** Erases the token and every Home Assistant setting: back to a phone that never connected. */
    suspend fun forgetConnection()

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setListMode(mode: HaListMode)

    suspend fun setAutoSync(enabled: Boolean)

    suspend fun setAutoCreateLists(enabled: Boolean)

    suspend fun setListsSetupDone()
}
