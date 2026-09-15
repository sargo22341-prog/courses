package org.opensources.courses.feature.homeassistant.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.courses.testing.FrenchCoursesTheme
import org.opensources.courses.core.model.SyncStatus
import org.opensources.courses.feature.homeassistant.domain.HaTodoList
import org.opensources.courses.feature.lists.domain.ShoppingList

@RunWith(AndroidJUnit4::class)
class HaListPickerDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val list = ShoppingList("l1", "Courses", isDefault = true, remoteId = null, createdByApp = false, syncStatus = SyncStatus.LOCAL_ONLY)

    @Test
    fun firstSetupOffersToCreateOrLinkEachExistingList() {
        var created = false
        var dismissed = false
        val state =
            HaSettingsUiState(
                lists = listOf(list),
                remoteLists = RemoteListsState.Loaded(listOf(HaTodoList("todo.liste_dachats", "Shopping list", supportsDescription = false))),
                setupListIds = listOf(list.id),
            )
        composeRule.setContent {
            FrenchCoursesTheme {
                HaListPickerDialog(list, state, onLink = {}, onCreate = { created = true }, onUnlink = {}, onDismiss = { dismissed = true })
            }
        }

        composeRule.onNodeWithText("Cette liste existe déjà sur ce téléphone", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Shopping list").assertIsDisplayed()
        composeRule.onNodeWithText("Créer « Courses » dans Home Assistant").performClick()
        composeRule.onNodeWithText("Plus tard").performClick()

        assertTrue(created)
        assertTrue(dismissed)
    }

    @Test
    fun unavailableHomeAssistantListCannotBeChosen() {
        var linked: String? = null
        val state =
            HaSettingsUiState(
                lists = listOf(list),
                remoteLists = RemoteListsState.Loaded(listOf(HaTodoList("todo.mon_agenda", "Mon agenda", supportsDescription = true, isAvailable = false))),
                pickerListId = list.id,
            )
        composeRule.setContent {
            FrenchCoursesTheme {
                HaListPickerDialog(list, state, onLink = { linked = it }, onCreate = {}, onUnlink = {}, onDismiss = {})
            }
        }

        composeRule.onNodeWithText("Indisponible dans Home Assistant").assertIsDisplayed()
        composeRule.onNodeWithText("Mon agenda").performClick()

        assertNull(linked)
    }
}
