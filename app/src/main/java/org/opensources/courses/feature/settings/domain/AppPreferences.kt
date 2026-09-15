package org.opensources.courses.feature.settings.domain

import kotlinx.coroutines.flow.Flow

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
}

/**
 * @property groupByCategory items to buy are shown under shop sections (off by default).
 */
data class AppPreferences(
    val themeMode: ThemeMode,
    val onboardingCompleted: Boolean,
    val hidePurchased: Boolean,
    val groupByCategory: Boolean = false,
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

    suspend fun setGroupByCategory(enabled: Boolean)
}
