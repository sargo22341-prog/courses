package org.opensources.courses.feature.catalog.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.opensources.courses.feature.catalog.domain.CatalogSyncInfo
import org.opensources.courses.feature.catalog.domain.CatalogSyncStateStore
import org.opensources.courses.feature.catalog.domain.SeedImportInfo
import org.opensources.courses.feature.language.domain.AppLanguage
import java.time.Instant
import javax.inject.Inject

class DataStoreCatalogSyncStateStore
    @Inject
    constructor(
        @CatalogDataStore private val dataStore: DataStore<Preferences>,
    ) : CatalogSyncStateStore {
        override val info: Flow<CatalogSyncInfo> =
            dataStore.data.map { preferences ->
                CatalogSyncInfo(
                    lastSyncAt = preferences[LAST_SYNC]?.let(Instant::ofEpochMilli),
                    version = preferences[VERSION],
                    formatVersion = preferences[FORMAT_VERSION] ?: 0,
                    language = preferences[LANGUAGE].toLanguage(),
                )
            }

        override suspend fun current(): CatalogSyncInfo = info.first()

        override suspend fun markSynced(
            at: Instant,
            version: String?,
            formatVersion: Int?,
            language: AppLanguage,
        ) {
            dataStore.edit { preferences ->
                preferences[LAST_SYNC] = at.toEpochMilli()
                if (version != null) preferences[VERSION] = version
                if (formatVersion != null) preferences[FORMAT_VERSION] = formatVersion
                preferences[LANGUAGE] = language.tag
            }
        }

        override suspend fun seedImport(): SeedImportInfo {
            val preferences = dataStore.data.first()
            return SeedImportInfo(preferences[SEED_VERSION] ?: 0, preferences[SEED_LANGUAGE].toLanguage())
        }

        override suspend fun markSeedImported(
            version: Int,
            language: AppLanguage,
        ) {
            dataStore.edit { preferences ->
                preferences[SEED_VERSION] = version
                preferences[SEED_LANGUAGE] = language.tag
            }
        }

        /** Catalogs imported before the app had languages were French, and none recorded its language. */
        private fun String?.toLanguage(): AppLanguage = this?.let(AppLanguage::fromTag) ?: AppLanguage.FRENCH

        private companion object {
            val LAST_SYNC = longPreferencesKey("last_sync_epoch_ms")
            val VERSION = stringPreferencesKey("catalog_version")
            val FORMAT_VERSION = intPreferencesKey("catalog_format_version")
            val LANGUAGE = stringPreferencesKey("catalog_language")
            val SEED_VERSION = intPreferencesKey("seed_version")
            val SEED_LANGUAGE = stringPreferencesKey("seed_language")
        }
    }
