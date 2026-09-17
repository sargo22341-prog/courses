package org.opensources.courses.feature.catalog.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import org.opensources.courses.feature.catalog.domain.CatalogImportInfo
import org.opensources.courses.feature.catalog.domain.CatalogImportStateStore
import org.opensources.courses.feature.catalog.domain.CatalogSource
import org.opensources.courses.feature.language.domain.AppLanguage
import javax.inject.Inject

class DataStoreCatalogImportStateStore
    @Inject
    constructor(
        @CatalogDataStore private val dataStore: DataStore<Preferences>,
    ) : CatalogImportStateStore {
        override suspend fun imported(source: CatalogSource): CatalogImportInfo {
            val preferences = dataStore.data.first()
            val language = preferences[languageKey(source)]?.let(AppLanguage::fromTag)
            // Catalogs imported before the app had languages were French, and none recorded its language.
            return CatalogImportInfo(preferences[versionKey(source)] ?: 0, language ?: AppLanguage.FRENCH)
        }

        override suspend fun markImported(
            source: CatalogSource,
            version: Int,
            language: AppLanguage,
        ) {
            dataStore.edit { preferences ->
                preferences[versionKey(source)] = version
                preferences[languageKey(source)] = language.tag
                // Written by the versions that downloaded the catalog every week; nothing reads them now.
                preferences.remove(DOWNLOAD_LAST_SYNC)
                preferences.remove(DOWNLOAD_VERSION)
                preferences.remove(DOWNLOAD_FORMAT_VERSION)
                preferences.remove(DOWNLOAD_LANGUAGE)
            }
        }

        private fun versionKey(source: CatalogSource) = intPreferencesKey("${source.name.lowercase()}_version")

        private fun languageKey(source: CatalogSource) = stringPreferencesKey("${source.name.lowercase()}_language")

        private companion object {
            val DOWNLOAD_LAST_SYNC = longPreferencesKey("last_sync_epoch_ms")
            val DOWNLOAD_VERSION = stringPreferencesKey("catalog_version")
            val DOWNLOAD_FORMAT_VERSION = intPreferencesKey("catalog_format_version")
            val DOWNLOAD_LANGUAGE = stringPreferencesKey("catalog_language")
        }
    }
