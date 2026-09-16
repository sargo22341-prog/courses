package org.opensources.courses.feature.settings.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.opensources.courses.feature.settings.domain.AppPreferences
import org.opensources.courses.feature.settings.domain.AppPreferencesRepository
import org.opensources.courses.feature.settings.domain.ThemeMode
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppSettingsDataStore

class DataStoreAppPreferencesRepository
    @Inject
    constructor(
        @AppSettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : AppPreferencesRepository {
        override val preferences: Flow<AppPreferences> =
            dataStore.data.map { preferences ->
                AppPreferences(
                    themeMode = preferences[THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
                    onboardingCompleted = preferences[ONBOARDING_COMPLETED] ?: false,
                    hidePurchased = preferences[HIDE_PURCHASED] ?: false,
                    groupByCategory = preferences[GROUP_BY_CATEGORY] ?: false,
                    languageConfirmed = preferences[LANGUAGE_CONFIRMED] ?: false,
                    historyEnabled = preferences[HISTORY_ENABLED] ?: true,
                )
            }

        override suspend fun setThemeMode(mode: ThemeMode) {
            dataStore.edit { it[THEME_MODE] = mode.name }
        }

        override suspend fun setOnboardingCompleted() {
            dataStore.edit { it[ONBOARDING_COMPLETED] = true }
        }

        override suspend fun setHidePurchased(hide: Boolean) {
            dataStore.edit { it[HIDE_PURCHASED] = hide }
        }

        override suspend fun setGroupByCategory(enabled: Boolean) {
            dataStore.edit { it[GROUP_BY_CATEGORY] = enabled }
        }

        override suspend fun setLanguageConfirmed() {
            dataStore.edit { it[LANGUAGE_CONFIRMED] = true }
        }

        override suspend fun setHistoryEnabled(enabled: Boolean) {
            dataStore.edit { it[HISTORY_ENABLED] = enabled }
        }

        private companion object {
            val THEME_MODE = stringPreferencesKey("theme_mode")
            val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
            val HIDE_PURCHASED = booleanPreferencesKey("hide_purchased")
            val GROUP_BY_CATEGORY = booleanPreferencesKey("group_by_category")
            val LANGUAGE_CONFIRMED = booleanPreferencesKey("language_confirmed")
            val HISTORY_ENABLED = booleanPreferencesKey("history_enabled")
        }
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsDataModule {
    @Binds
    abstract fun bindAppPreferencesRepository(repository: DataStoreAppPreferencesRepository): AppPreferencesRepository

    companion object {
        @Provides
        @Singleton
        @AppSettingsDataStore
        fun appSettingsDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("app_settings") }
    }
}
