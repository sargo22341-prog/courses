package org.opensources.courses.feature.settings.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.courses.feature.settings.presentation.components.HistorySection
import org.opensources.courses.testing.FrenchCoursesTheme

@RunWith(AndroidJUnit4::class)
class HistorySectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clearingTheHistoryAsksForConfirmation() {
        var cleared = false
        composeRule.setContent {
            FrenchCoursesTheme { HistorySection(HistorySettings(enabled = true, hasHistory = true), onEnabledChange = {}, onClear = { cleared = true }) }
        }

        composeRule.onNodeWithText("Vider l’historique").performClick()
        composeRule.onNodeWithText("Vider l’historique ?").assertIsDisplayed()
        composeRule.onNodeWithText("Annuler").performClick()
        assertFalse(cleared)

        composeRule.onNodeWithText("Vider l’historique").performClick()
        composeRule.onNodeWithText("Vider").performClick()

        assertTrue(cleared)
        composeRule.onNodeWithText("Vider l’historique ?").assertDoesNotExist()
    }

    @Test
    fun anEmptyHistoryCannotBeClearedAndCanBeTurnedOn() {
        var enabled: Boolean? = null
        composeRule.setContent {
            FrenchCoursesTheme { HistorySection(HistorySettings(enabled = false, hasHistory = false), onEnabledChange = { enabled = it }, onClear = {}) }
        }

        composeRule.onNodeWithText("Vider l’historique").assertIsNotEnabled()
        composeRule.onNodeWithText("L’historique est vide.").assertIsDisplayed()
        val toggle = composeRule.onNode(isToggleable())
        toggle.assertIsOff()
        toggle.performClick()

        assertEquals(true, enabled)
    }
}
