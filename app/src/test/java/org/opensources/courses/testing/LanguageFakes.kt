package org.opensources.courses.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.opensources.courses.feature.language.domain.AppLanguage
import org.opensources.courses.feature.language.domain.AppLanguageRepository
import org.opensources.courses.feature.settings.domain.AppPreferences
import org.opensources.courses.feature.settings.domain.AppPreferencesRepository
import org.opensources.courses.feature.settings.domain.ThemeMode

/** Same contract as the Android per-app language: choosing the displayed language changes nothing. */
class FakeAppLanguageRepository(
    initial: AppLanguage = AppLanguage.FRENCH,
    var chosen: Boolean = false,
) : AppLanguageRepository {
    override val language = MutableStateFlow(initial)

    override fun hasChosenLanguage(): Boolean = chosen

    override fun setLanguage(language: AppLanguage) {
        if (language == this.language.value) return
        this.language.value = language
        chosen = true
    }

    override fun refresh() = Unit
}

class FakeAppPreferencesRepository(
    initial: AppPreferences = AppPreferences.Default,
) : AppPreferencesRepository {
    val state = MutableStateFlow(initial)
    override val preferences: Flow<AppPreferences> = state

    override suspend fun setThemeMode(mode: ThemeMode) = state.update { it.copy(themeMode = mode) }

    override suspend fun setOnboardingCompleted() = state.update { it.copy(onboardingCompleted = true) }

    override suspend fun setHidePurchased(hide: Boolean) = state.update { it.copy(hidePurchased = hide) }

    override suspend fun setGroupByCategory(enabled: Boolean) = state.update { it.copy(groupByCategory = enabled) }

    override suspend fun setLanguageConfirmed() = state.update { it.copy(languageConfirmed = true) }

    override suspend fun setHistoryEnabled(enabled: Boolean) = state.update { it.copy(historyEnabled = enabled) }
}
