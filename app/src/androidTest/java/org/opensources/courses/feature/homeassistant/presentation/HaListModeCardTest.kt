package org.opensources.courses.feature.homeassistant.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.courses.core.designsystem.theme.CoursesTheme
import org.opensources.courses.feature.homeassistant.domain.HaListMode

@RunWith(AndroidJUnit4::class)
class HaListModeCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var chosen: HaListMode? = null

    private fun show(
        mode: HaListMode,
        importedListCount: Int,
    ) = composeRule.setContent { CoursesTheme { HaListModeCard(mode, importedListCount, onModeChange = { chosen = it }) } }

    @Test
    fun leavingAllListsAsksBeforeRemovingTheImportedLists() {
        show(HaListMode.ALL_LISTS, importedListCount = 2)

        composeRule.onNodeWithText(APP_ONLY).performClick()
        composeRule.onNodeWithText("2 listes importées de Home Assistant", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Annuler").performClick()
        assertNull(chosen)

        composeRule.onNodeWithText(APP_ONLY).performClick()
        composeRule.onNodeWithText("Retirer").performClick()
        assertEquals(HaListMode.APP_CREATED_ONLY, chosen)
    }

    @Test
    fun choosingAllListsOrLeavingWithoutImportedListsNeedsNoConfirmation() {
        show(HaListMode.APP_CREATED_ONLY, importedListCount = 0)

        composeRule.onNodeWithText("Toutes les listes").performClick()

        assertEquals(HaListMode.ALL_LISTS, chosen)
        composeRule.onNodeWithText("Retirer les listes importées ?").assertDoesNotExist()
    }

    private companion object {
        const val APP_ONLY = "Uniquement les listes créées par cette application"
    }
}
