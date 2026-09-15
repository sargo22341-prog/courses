package org.opensources.courses.feature.settings.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.courses.testing.FrenchCoursesTheme
import org.opensources.courses.core.sync.SyncSnapshot
import org.opensources.courses.core.sync.SyncState
import org.opensources.courses.feature.settings.presentation.components.HomeAssistantEntry

@RunWith(AndroidJUnit4::class)
class HomeAssistantEntryTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        status: HomeAssistantStatus,
        onOpen: () -> Unit = {},
    ) = composeRule.setContent { FrenchCoursesTheme { HomeAssistantEntry(status, onOpen) } }

    @Test
    fun connectedWithSynchronisationOffIsNotShownAsDisconnected() {
        show(HomeAssistantStatus.Connected(URL, sync = null))

        composeRule.onNodeWithText("Connecté").assertIsDisplayed()
        composeRule.onNodeWithText("Synchronisation désactivée").assertIsDisplayed()
        composeRule.onNodeWithText(URL).assertIsDisplayed()
        composeRule.onNodeWithText("Non connecté — facultatif").assertDoesNotExist()
    }

    @Test
    fun connectedWithSynchronisationOnShowsItsState() {
        show(HomeAssistantStatus.Connected(URL, SyncSnapshot(SyncState.ONLINE, remoteEnabled = true, pendingCount = 2, failure = null)))

        composeRule.onNodeWithText("Connecté").assertIsDisplayed()
        composeRule.onNodeWithText("Synchronisé · 2 modifications en attente").assertIsDisplayed()
    }

    @Test
    fun notConfiguredStaysOptionalAndOpensTheScreen() {
        var opened = false
        show(HomeAssistantStatus.NotConfigured, onOpen = { opened = true })

        composeRule.onNodeWithText("Non connecté — facultatif").assertIsDisplayed().performClick()

        assertTrue(opened)
    }

    private companion object {
        const val URL = "http://ha.local:8123"
    }
}
