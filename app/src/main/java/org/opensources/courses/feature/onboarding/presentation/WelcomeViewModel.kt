package org.opensources.courses.feature.onboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.opensources.courses.feature.language.domain.AppLanguage
import org.opensources.courses.feature.language.domain.AppLanguageRepository
import org.opensources.courses.feature.onboarding.domain.CompleteOnboardingUseCase
import javax.inject.Inject

/** @property language preselected with the device language; picking another one translates the screen at once. */
data class WelcomeUiState(
    val language: AppLanguage,
    val completing: Boolean = false,
)

@HiltViewModel
class WelcomeViewModel
    @Inject
    constructor(
        private val completeOnboarding: CompleteOnboardingUseCase,
        private val languages: AppLanguageRepository,
    ) : ViewModel() {
        private val completing = MutableStateFlow(false)

        val uiState: StateFlow<WelcomeUiState> =
            combine(languages.language, completing, ::WelcomeUiState)
                .stateIn(viewModelScope, SharingStarted.Eagerly, WelcomeUiState(languages.language.value))

        fun selectLanguage(language: AppLanguage) = languages.setLanguage(language)

        fun complete(onCompleted: () -> Unit) {
            if (completing.value) return
            completing.value = true
            viewModelScope.launch {
                completeOnboarding()
                onCompleted()
            }
        }
    }
