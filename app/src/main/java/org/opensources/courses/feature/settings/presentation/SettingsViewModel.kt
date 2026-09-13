package org.opensources.courses.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.opensources.courses.feature.homeassistant.domain.HaConfigRepository
import org.opensources.courses.feature.settings.domain.AppPreferencesRepository
import org.opensources.courses.feature.settings.domain.ThemeMode
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val homeAssistantEnabled: Boolean = false,
    val homeAssistantUrl: String = "",
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val preferences: AppPreferencesRepository,
        haConfig: HaConfigRepository,
    ) : ViewModel() {
        val uiState: StateFlow<SettingsUiState> =
            combine(preferences.preferences, haConfig.config) { prefs, ha ->
                SettingsUiState(prefs.themeMode, ha.enabled && ha.isConfigured, ha.baseUrl)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

        fun setThemeMode(mode: ThemeMode) {
            viewModelScope.launch { preferences.setThemeMode(mode) }
        }
    }
