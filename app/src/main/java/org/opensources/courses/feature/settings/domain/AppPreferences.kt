package org.opensources.courses.feature.settings.domain

import kotlinx.coroutines.flow.Flow

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
}

data class AppPreferences(
    val themeMode: ThemeMode,
    val onboardingCompleted: Boolean,
    val hidePurchased: Boolean,
) {
    companion object {
        val Default = AppPreferences(ThemeMode.SYSTEM, onboardingCompleted = false, hidePurchased = false)
    }
}

interface AppPreferencesRepository {
    val preferences: Flow<AppPreferences>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setOnboardingCompleted()

    suspend fun setHidePurchased(hide: Boolean)
}
