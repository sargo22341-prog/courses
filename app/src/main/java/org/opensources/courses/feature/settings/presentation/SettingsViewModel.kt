package org.opensources.courses.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.opensources.courses.core.sync.SyncCoordinator
import org.opensources.courses.feature.catalog.domain.CatalogRepository
import org.opensources.courses.feature.homeassistant.domain.HaConfigRepository
import org.opensources.courses.feature.language.domain.AppLanguage
import org.opensources.courses.feature.language.domain.AppLanguageRepository
import org.opensources.courses.feature.settings.domain.AppPreferencesRepository
import org.opensources.courses.feature.settings.domain.ThemeMode
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.Fallback,
    val groupByCategory: Boolean = false,
    val homeAssistant: HomeAssistantStatus = HomeAssistantStatus.NotConfigured,
    val history: HistorySettings = HistorySettings(),
)

/** @property hasHistory at least one product was added since the history was last cleared. */
data class HistorySettings(
    val enabled: Boolean = true,
    val hasHistory: Boolean = false,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val preferences: AppPreferencesRepository,
        private val languages: AppLanguageRepository,
        private val catalog: CatalogRepository,
        haConfig: HaConfigRepository,
        syncCoordinator: SyncCoordinator,
    ) : ViewModel() {
        val uiState: StateFlow<SettingsUiState> =
            combine(
                preferences.preferences,
                languages.language,
                haConfig.config,
                syncCoordinator.snapshot,
                catalog.observeHasUsage(),
            ) { prefs, language, ha, sync, hasHistory ->
                SettingsUiState(
                    themeMode = prefs.themeMode,
                    language = language,
                    groupByCategory = prefs.groupByCategory,
                    homeAssistant = HomeAssistantStatus.from(ha, sync),
                    history = HistorySettings(prefs.historyEnabled, hasHistory),
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(language = languages.language.value))

        fun setThemeMode(mode: ThemeMode) {
            viewModelScope.launch { preferences.setThemeMode(mode) }
        }

        /** The screen is recreated in [language]; the catalog follows in the background (see AppInitializer). */
        fun setLanguage(language: AppLanguage) = languages.setLanguage(language)

        fun setGroupByCategory(enabled: Boolean) {
            viewModelScope.launch { preferences.setGroupByCategory(enabled) }
        }

        fun setHistoryEnabled(enabled: Boolean) {
            viewModelScope.launch { preferences.setHistoryEnabled(enabled) }
        }

        /** Only called once the user confirmed: past additions are forgotten for good. */
        fun clearHistory() {
            viewModelScope.launch { catalog.clearUsage() }
        }
    }
