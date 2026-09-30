package org.opensources.courses.feature.homeassistant.domain

import kotlinx.coroutines.flow.Flow

interface HaConfigRepository {
    val config: Flow<HomeAssistantConfig>

    /** Credentials to use for synchronisation, or null when Home Assistant is disabled or incomplete. */
    suspend fun credentials(): HaCredentials?

    /**
     * Credentials for a connection test: the typed token or, when [typedToken] is blank, the stored
     * one if [baseUrl] names the server it was saved for ([HaUrlNormalizer.sameHost]).
     */
    suspend fun credentialsFor(
        baseUrl: String,
        typedToken: String,
    ): HaCredentials?

    /** Saves the address and, when not blank, replaces the stored token. */
    suspend fun saveConnection(
        baseUrl: String,
        token: String,
    ): HaSaveResult

    /** Erases the token and every Home Assistant setting: back to a phone that never connected. */
    suspend fun forgetConnection()

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setListMode(mode: HaListMode)

    suspend fun setAutoSync(enabled: Boolean)

    suspend fun setAutoCreateLists(enabled: Boolean)

    suspend fun setListsSetupDone()
}

enum class HaSaveResult {
    SAVED,

    /** Not an http(s) address: nothing is saved. */
    INVALID_URL,

    /**
     * The address names another server and no token was typed: nothing is saved, since the stored
     * token must never be sent to another server than the one it was saved for.
     */
    TOKEN_REQUIRED,
}
