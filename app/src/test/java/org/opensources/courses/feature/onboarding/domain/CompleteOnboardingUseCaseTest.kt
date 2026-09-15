package org.opensources.courses.feature.onboarding.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.courses.feature.language.domain.AppLanguage
import org.opensources.courses.feature.lists.domain.ShoppingListDefaults
import org.opensources.courses.testing.FakeAppLanguageRepository
import org.opensources.courses.testing.FakeAppPreferencesRepository
import org.opensources.courses.testing.FakeShoppingListRepository

class CompleteOnboardingUseCaseTest {
    private val lists = FakeShoppingListRepository()
    private val preferences = FakeAppPreferencesRepository()

    @Test
    fun `the default list is named in the language shown on the welcome screen`() =
        runTest {
            val languages = FakeAppLanguageRepository(AppLanguage.GERMAN)

            val list = CompleteOnboardingUseCase(lists, preferences, languages)()

            assertEquals("Einkaufsliste", list.name)
            assertTrue(list.isDefault)
            assertTrue(preferences.state.value.onboardingCompleted)
            assertTrue(preferences.state.value.languageConfirmed)
        }

    @Test
    fun `completing never changes the language, so the screen is not recreated while navigating`() =
        runTest {
            val languages = FakeAppLanguageRepository(AppLanguage.SPANISH)

            CompleteOnboardingUseCase(lists, preferences, languages)()

            assertEquals(AppLanguage.SPANISH, languages.language.value)
            assertEquals(false, languages.chosen)
        }

    @Test
    fun `every language has its own default list name`() {
        val names = AppLanguage.entries.map { ShoppingListDefaults.defaultListName(it) }

        assertEquals("Courses", ShoppingListDefaults.defaultListName(AppLanguage.FRENCH))
        assertTrue(names.all { it.isNotBlank() })
    }
}
