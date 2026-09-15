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
import org.opensources.courses.feature.homeassistant.domain.HaConfigRepository
import org.opensources.courses.feature.settings.domain.AppPreferencesRepository
import org.opensources.courses.feature.settings.domain.ThemeMode
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val groupByCategory: Boolean = false,
    val homeAssistant: HomeAssistantStatus = HomeAssistantStatus.NotConfigured,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val preferences: AppPreferencesRepository,
        haConfig: HaConfigRepository,
        syncCoordinator: SyncCoordinator,
    ) : ViewModel() {
        val uiState: StateFlow<SettingsUiState> =
            combine(preferences.preferences, haConfig.config, syncCoordinator.snapshot) { prefs, ha, sync ->
                SettingsUiState(prefs.themeMode, prefs.groupByCategory, HomeAssistantStatus.from(ha, sync))
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

        fun setThemeMode(mode: ThemeMode) {
            viewModelScope.launch { preferences.setThemeMode(mode) }
        }

        fun setGroupByCategory(enabled: Boolean) {
            viewModelScope.launch { preferences.setGroupByCategory(enabled) }
        }
    }
