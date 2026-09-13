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
                )
            }

        override suspend fun current(): CatalogSyncInfo = info.first()

        override suspend fun markSynced(
            at: Instant,
            version: String?,
        ) {
            dataStore.edit { preferences ->
                preferences[LAST_SYNC] = at.toEpochMilli()
                if (version != null) preferences[VERSION] = version
            }
        }

        override suspend fun seedVersion(): Int = dataStore.data.first()[SEED_VERSION] ?: 0

        override suspend fun setSeedVersion(version: Int) {
            dataStore.edit { it[SEED_VERSION] = version }
        }

        private companion object {
            val LAST_SYNC = longPreferencesKey("last_sync_epoch_ms")
            val VERSION = stringPreferencesKey("catalog_version")
            val SEED_VERSION = intPreferencesKey("seed_version")
        }
    }
