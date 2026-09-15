package org.opensources.courses.feature.onboarding.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.courses.feature.language.domain.AppLanguage
import org.opensources.courses.testing.FrenchCoursesTheme

@RunWith(AndroidJUnit4::class)
class WelcomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var started = false
    private var connect = false
    private var chosen: AppLanguage? = null

    private fun show() =
        composeRule.setContent {
            FrenchCoursesTheme {
                WelcomeScreen(
                    language = AppLanguage.FRENCH,
                    enabled = true,
                    onLanguageSelected = { chosen = it },
                    onStart = { started = true },
                    onConnectHomeAssistant = { connect = true },
                )
            }
        }

    @Test
    fun startIsTheOnlyRequiredStep() {
        show()

        composeRule.onNodeWithText("Mes Courses").assertIsDisplayed()
        composeRule.onNodeWithText("Connecter Home Assistant").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Commencer").performScrollTo().performClick()

        assertTrue(started)
        assertTrue(!connect)
        assertEquals(null, chosen)
    }

    @Test
    fun everyLanguageIsOfferedInItselfWithTheCurrentOneSelected() {
        show()

        composeRule.onNodeWithText("Langue").assertIsDisplayed()
        composeRule.onNodeWithText("Français").assertIsSelected()
        listOf("Deutsch", "English", "Español", "Italiano", "Português").forEach { composeRule.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
        composeRule.onNodeWithText("Italiano").performClick()

        assertEquals(AppLanguage.ITALIAN, chosen)
    }
}
