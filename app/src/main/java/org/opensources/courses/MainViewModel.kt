package org.opensources.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.opensources.courses.feature.settings.domain.AppPreferencesRepository
import org.opensources.courses.feature.settings.domain.ThemeMode
import javax.inject.Inject

sealed interface MainUiState {
    data object Loading : MainUiState

    data class Ready(
        val themeMode: ThemeMode,
        val onboardingCompleted: Boolean,
    ) : MainUiState
}

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        preferences: AppPreferencesRepository,
    ) : ViewModel() {
        val uiState: StateFlow<MainUiState> =
            preferences.preferences
                .map { MainUiState.Ready(it.themeMode, it.onboardingCompleted) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)
    }
