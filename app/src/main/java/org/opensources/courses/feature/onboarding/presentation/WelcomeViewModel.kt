package org.opensources.courses.feature.onboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.opensources.courses.feature.lists.domain.ShoppingListDefaults
import org.opensources.courses.feature.onboarding.domain.CompleteOnboardingUseCase
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel
    @Inject
    constructor(
        private val completeOnboarding: CompleteOnboardingUseCase,
    ) : ViewModel() {
        private val mutableCompleting = MutableStateFlow(false)
        val completing: StateFlow<Boolean> = mutableCompleting.asStateFlow()

        fun complete(onCompleted: () -> Unit) {
            if (mutableCompleting.value) return
            mutableCompleting.value = true
            viewModelScope.launch {
                completeOnboarding(ShoppingListDefaults.DEFAULT_LIST_NAME)
                onCompleted()
            }
        }
    }
