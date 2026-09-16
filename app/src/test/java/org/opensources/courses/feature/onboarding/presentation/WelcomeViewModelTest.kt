package org.opensources.courses.feature.onboarding.presentation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.opensources.courses.feature.language.domain.AppLanguage
import org.opensources.courses.feature.onboarding.domain.CompleteOnboardingUseCase
import org.opensources.courses.testing.FakeAppLanguageRepository
import org.opensources.courses.testing.FakeAppPreferencesRepository
import org.opensources.courses.testing.FakeShoppingListRepository
import org.opensources.courses.testing.MainDispatcherRule

class WelcomeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val lists = FakeShoppingListRepository()
    private val preferences = FakeAppPreferencesRepository()
    private val languages = FakeAppLanguageRepository(AppLanguage.FRENCH)
    // Created once the rule installed the main dispatcher.
    private val viewModel by lazy { WelcomeViewModel(CompleteOnboardingUseCase(lists, preferences, languages), languages) }

    @Test
    fun `the screen leaves only once the onboarding is saved`() =
        runTest {
            assertNull(viewModel.uiState.value.exit)

            viewModel.complete(WelcomeExit.HOME_ASSISTANT)

            assertTrue(preferences.state.value.onboardingCompleted)
            assertEquals(WelcomeExit.HOME_ASSISTANT, viewModel.uiState.value.exit)
            assertTrue(viewModel.uiState.value.completing)
        }

    @Test
    fun `a second tap is ignored`() =
        runTest {
            viewModel.complete(WelcomeExit.SHOPPING)
            viewModel.complete(WelcomeExit.HOME_ASSISTANT)

            assertEquals(WelcomeExit.SHOPPING, viewModel.uiState.value.exit)
            assertEquals(1, lists.lists.value.size)
        }

    @Test
    fun `picking a language translates the screen`() =
        runTest {
            viewModel.selectLanguage(AppLanguage.ITALIAN)

            assertEquals(AppLanguage.ITALIAN, viewModel.uiState.value.language)
        }
}
