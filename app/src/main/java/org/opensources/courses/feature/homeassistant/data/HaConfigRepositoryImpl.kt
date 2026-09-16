package org.opensources.courses.feature.homeassistant.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.opensources.courses.core.security.SecretStore
import org.opensources.courses.feature.homeassistant.domain.HaConfigRepository
import org.opensources.courses.feature.homeassistant.domain.HaCredentials
import org.opensources.courses.feature.homeassistant.domain.HaListMode
import org.opensources.courses.feature.homeassistant.domain.HaUrlNormalizer
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantConfig
import javax.inject.Inject

/**
 * Non-sensitive settings live in the `home_assistant` DataStore; the token is handed to the
 * Keystore-backed [SecretStore] and only a "token present" flag is kept here.
 */
class HaConfigRepositoryImpl
    @Inject
    constructor(
        @HomeAssistantDataStore private val dataStore: DataStore<Preferences>,
        private val secrets: SecretStore,
    ) : HaConfigRepository {
        override val config: Flow<HomeAssistantConfig> =
            dataStore.data.map { preferences ->
                HomeAssistantConfig(
                    enabled = preferences[ENABLED] ?: false,
                    baseUrl = preferences[BASE_URL].orEmpty(),
                    hasToken = preferences[HAS_TOKEN] ?: false,
                    listMode = preferences[LIST_MODE]?.let { runCatching { HaListMode.valueOf(it) }.getOrNull() } ?: HaListMode.APP_CREATED_ONLY,
                    autoSync = preferences[AUTO_SYNC] ?: true,
                    autoCreateLists = preferences[AUTO_CREATE_LISTS] ?: true,
                    listsSetupDone = preferences[LISTS_SETUP_DONE] ?: false,
                    tokenVersion = preferences[TOKEN_VERSION] ?: 0,
                )
            }

        override suspend fun credentials(): HaCredentials? {
            val current = config.first()
            if (!current.enabled || !current.isConfigured) return null
            val token = secrets.read(TOKEN_SECRET) ?: return null
            return HaCredentials(current.baseUrl, token)
        }

        override suspend fun credentialsFor(
            baseUrl: String,
            typedToken: String,
        ): HaCredentials? {
            val url = HaUrlNormalizer.normalize(baseUrl) ?: return null
            val token = typedToken.trim().ifEmpty { secrets.read(TOKEN_SECRET) } ?: return null
            return HaCredentials(url, token)
        }

        override suspend fun saveConnection(
            baseUrl: String,
            token: String,
        ): Boolean {
            val url = HaUrlNormalizer.normalize(baseUrl) ?: return false
            val cleanToken = token.trim()
            if (cleanToken.isNotEmpty()) secrets.write(TOKEN_SECRET, cleanToken)
            dataStore.edit { preferences ->
                preferences[BASE_URL] = url
                if (cleanToken.isNotEmpty()) {
                    preferences[HAS_TOKEN] = true
                    preferences[TOKEN_VERSION] = (preferences[TOKEN_VERSION] ?: 0) + 1
                }
            }
            return true
        }

        override suspend fun forgetConnection() {
            // Settings first: once disabled, nothing reads the token any more.
            dataStore.edit { it.clear() }
            secrets.remove(TOKEN_SECRET)
        }

        override suspend fun setEnabled(enabled: Boolean) {
            dataStore.edit { it[ENABLED] = enabled }
        }

        override suspend fun setListMode(mode: HaListMode) {
            dataStore.edit { it[LIST_MODE] = mode.name }
        }

        override suspend fun setAutoSync(enabled: Boolean) {
            dataStore.edit { it[AUTO_SYNC] = enabled }
        }

        override suspend fun setAutoCreateLists(enabled: Boolean) {
            dataStore.edit { it[AUTO_CREATE_LISTS] = enabled }
        }

        override suspend fun setListsSetupDone() {
            dataStore.edit { it[LISTS_SETUP_DONE] = true }
        }

        private companion object {
            const val TOKEN_SECRET = "home_assistant_token"
            val ENABLED = booleanPreferencesKey("enabled")
            val BASE_URL = stringPreferencesKey("base_url")
            val HAS_TOKEN = booleanPreferencesKey("has_token")
            val LIST_MODE = stringPreferencesKey("list_mode")
            val AUTO_SYNC = booleanPreferencesKey("auto_sync")
            val AUTO_CREATE_LISTS = booleanPreferencesKey("auto_create_lists")
            val LISTS_SETUP_DONE = booleanPreferencesKey("lists_setup_done")
            val TOKEN_VERSION = intPreferencesKey("token_version")
        }
    }
