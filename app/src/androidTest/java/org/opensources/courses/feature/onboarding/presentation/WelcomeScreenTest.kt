package org.opensources.courses.feature.onboarding.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.courses.core.designsystem.theme.CoursesTheme

@RunWith(AndroidJUnit4::class)
class WelcomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun startIsTheOnlyRequiredStep() {
        var started = false
        var connect = false
        composeRule.setContent {
            CoursesTheme { WelcomeScreen(enabled = true, onStart = { started = true }, onConnectHomeAssistant = { connect = true }) }
        }

        composeRule.onNodeWithText("Mes Courses").assertIsDisplayed()
        composeRule.onNodeWithText("Connecter Home Assistant").assertIsDisplayed()
        composeRule.onNodeWithText("Commencer").performClick()

        assertTrue(started)
        assertTrue(!connect)
    }
}
