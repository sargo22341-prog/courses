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

/** Where the user goes once the welcome screen is done. */
enum class WelcomeExit {
    SHOPPING,
    HOME_ASSISTANT,
}

/**
 * @property language preselected with the device language; picking another one translates the screen at once.
 * @property exit set once the onboarding is saved: the screen then navigates.
 */
data class WelcomeUiState(
    val language: AppLanguage,
    val completing: Boolean = false,
    val exit: WelcomeExit? = null,
)

@HiltViewModel
class WelcomeViewModel
    @Inject
    constructor(
        private val completeOnboarding: CompleteOnboardingUseCase,
        private val languages: AppLanguageRepository,
    ) : ViewModel() {
        private val completing = MutableStateFlow(false)
        private val exit = MutableStateFlow<WelcomeExit?>(null)

        val uiState: StateFlow<WelcomeUiState> =
            combine(languages.language, completing, exit, ::WelcomeUiState)
                .stateIn(viewModelScope, SharingStarted.Eagerly, WelcomeUiState(languages.language.value))

        fun selectLanguage(language: AppLanguage) = languages.setLanguage(language)

        /** A second tap while the first one is being saved is ignored. */
        fun complete(next: WelcomeExit) {
            if (completing.value) return
            completing.value = true
            viewModelScope.launch {
                completeOnboarding()
                exit.value = next
            }
        }
    }
