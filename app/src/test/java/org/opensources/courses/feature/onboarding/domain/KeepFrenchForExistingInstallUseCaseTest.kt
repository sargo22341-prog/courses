package org.opensources.courses.feature.onboarding.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.courses.feature.language.domain.AppLanguage
import org.opensources.courses.feature.settings.domain.AppPreferences
import org.opensources.courses.testing.FakeAppLanguageRepository
import org.opensources.courses.testing.FakeAppPreferencesRepository

class KeepFrenchForExistingInstallUseCaseTest {
    private fun preferences(
        onboardingCompleted: Boolean,
        languageConfirmed: Boolean = false,
    ) = FakeAppPreferencesRepository(AppPreferences.Default.copy(onboardingCompleted = onboardingCompleted, languageConfirmed = languageConfirmed))

    @Test
    fun `an install that predates languages stays in French on a phone in another language`() =
        runTest {
            val preferences = preferences(onboardingCompleted = true)
            val languages = FakeAppLanguageRepository(AppLanguage.ENGLISH)

            KeepFrenchForExistingInstallUseCase(preferences, languages)()

            assertEquals(AppLanguage.FRENCH, languages.language.value)
            assertTrue(preferences.state.value.languageConfirmed)
        }

    @Test
    fun `a language already chosen in the Android settings is kept`() =
        runTest {
            val preferences = preferences(onboardingCompleted = true)
            val languages = FakeAppLanguageRepository(AppLanguage.GERMAN, chosen = true)

            KeepFrenchForExistingInstallUseCase(preferences, languages)()

            assertEquals(AppLanguage.GERMAN, languages.language.value)
            assertTrue(preferences.state.value.languageConfirmed)
        }

    @Test
    fun `a new install chooses its language on the welcome screen`() =
        runTest {
            val preferences = preferences(onboardingCompleted = false)
            val languages = FakeAppLanguageRepository(AppLanguage.SPANISH)

            KeepFrenchForExistingInstallUseCase(preferences, languages)()

            assertEquals(AppLanguage.SPANISH, languages.language.value)
            assertFalse(preferences.state.value.languageConfirmed)
        }

    @Test
    fun `runs only once, so a language later reset to the device one is respected`() =
        runTest {
            val preferences = preferences(onboardingCompleted = true, languageConfirmed = true)
            val languages = FakeAppLanguageRepository(AppLanguage.ITALIAN)

            KeepFrenchForExistingInstallUseCase(preferences, languages)()

            assertEquals(AppLanguage.ITALIAN, languages.language.value)
        }
}
