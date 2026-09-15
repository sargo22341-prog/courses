package org.opensources.courses.feature.homeassistant.presentation

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
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantConfig

@RunWith(AndroidJUnit4::class)
class HaConnectionCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setCard(
        state: HaSettingsUiState = HaSettingsUiState(),
        onScanToken: () -> Unit = {},
    ) {
        composeRule.setContent {
            FrenchCoursesTheme {
                HaConnectionCard(
                    state = state,
                    url = "",
                    token = "",
                    onUrlChange = {},
                    onTokenChange = {},
                    onScanToken = onScanToken,
                    onEnabledChange = {},
                    onSave = {},
                    onTest = {},
                )
            }
        }
    }

    @Test
    fun scanButtonOpensTheScanner() {
        var scanRequested = false
        setCard(onScanToken = { scanRequested = true })

        composeRule.onNodeWithText("Scanner le QR code du token").performClick()

        assertTrue(scanRequested)
    }

    @Test
    fun scanResultIsExplained() {
        setCard(state = HaSettingsUiState(connection = HaActionStatus.Done(HaMessage.INVALID_TOKEN_QR)))

        composeRule.onNodeWithText("Ce QR code ne contient pas de token Home Assistant.").assertIsDisplayed()
    }

    @Test
    fun savedConnectionIsFoldedUntilConnectedIsTapped() {
        val saved = HomeAssistantConfig.Default.copy(enabled = true, baseUrl = "https://ha.nas.home", hasToken = true)
        setCard(state = HaSettingsUiState(config = saved, isLoaded = true))

        composeRule.onNodeWithText("Adresse de Home Assistant").assertDoesNotExist()
        composeRule.onNodeWithText("Activer la synchronisation").assertIsDisplayed()

        composeRule.onNodeWithText("Connecté").performClick()

        composeRule.onNodeWithText("Adresse de Home Assistant").assertIsDisplayed()
        composeRule.onNodeWithText("Tester la connexion").assertIsDisplayed()
    }
}
