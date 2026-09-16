package org.opensources.courses.feature.homeassistant.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
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
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantConfig
import org.opensources.courses.testing.FrenchCoursesTheme

@RunWith(AndroidJUnit4::class)
class HaConnectionCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val saved = HomeAssistantConfig.Default.copy(enabled = true, baseUrl = "https://ha.nas.home", hasToken = true)

    private fun setCard(
        state: HaSettingsUiState = HaSettingsUiState(),
        onScanToken: () -> Unit = {},
        onForget: () -> Unit = {},
    ) {
        composeRule.setContent {
            FrenchCoursesTheme {
                // Scrollable like the real screen: the unfolded card is taller than some phones.
                Column(Modifier.verticalScroll(rememberScrollState())) {
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
                        onForget = onForget,
                    )
                }
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
        setCard(state = HaSettingsUiState(config = saved, isLoaded = true))

        composeRule.onNodeWithText("Adresse de Home Assistant").assertDoesNotExist()
        composeRule.onNodeWithText("Activer la synchronisation").assertIsDisplayed()

        composeRule.onNodeWithText("Connecté").performClick()

        composeRule.onNodeWithText("Adresse de Home Assistant").assertIsDisplayed()
        composeRule.onNodeWithText("Tester la connexion").assertIsDisplayed()
    }

    @Test
    fun connectionIsForgottenOnlyAfterConfirmation() {
        var forgotten = 0
        setCard(state = HaSettingsUiState(config = saved, isLoaded = true), onForget = { forgotten++ })
        composeRule.onNodeWithText("Connecté").performClick()

        composeRule.onNodeWithText("Oublier la connexion").performScrollTo().performClick()
        composeRule.onNodeWithText("Annuler").performClick()
        assertEquals(0, forgotten)

        composeRule.onNodeWithText("Oublier la connexion").performScrollTo().performClick()
        composeRule.onNodeWithText("Oublier").performClick()
        assertEquals(1, forgotten)
    }

    @Test
    fun nothingToForgetBeforeAConnectionIsSaved() {
        setCard()

        composeRule.onNodeWithText("Oublier la connexion").assertDoesNotExist()
    }

    @Test
    fun plainHttpAddressWarnsThatTheTokenIsReadable() {
        setCard(state = HaSettingsUiState(config = saved.copy(baseUrl = "http://homeassistant.local:8123"), isLoaded = true))

        composeRule.onNodeWithText("le token circule en clair", substring = true).assertIsDisplayed()
    }

    @Test
    fun httpsAddressShowsNoWarning() {
        setCard(state = HaSettingsUiState(config = saved, isLoaded = true))

        composeRule.onNodeWithText("le token circule en clair", substring = true).assertDoesNotExist()
    }
}
