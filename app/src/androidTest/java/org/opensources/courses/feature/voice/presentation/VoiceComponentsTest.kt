package org.opensources.courses.feature.voice.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.courses.feature.voice.presentation.components.VoiceHoldButton
import org.opensources.courses.feature.voice.presentation.components.VoiceReviewDialog
import org.opensources.courses.testing.FrenchCoursesTheme

@RunWith(AndroidJUnit4::class)
class VoiceComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun microphoneCenterStaysFixedWhileListeningAndProcessing() {
        val listening = mutableStateOf(false)
        val processing = mutableStateOf(false)
        compose.setContent {
            FrenchCoursesTheme {
                Scaffold(floatingActionButton = {
                    VoiceHoldButton(true, listening.value, processing.value, {}, {}, {})
                }) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding))
                }
            }
        }
        val button = compose.onNodeWithContentDescription("Maintenir pour dicter les courses")
        val restingBounds = button.fetchSemanticsNode().boundsInRoot
        val center = restingBounds.center
        compose.runOnIdle { listening.value = true }
        compose.waitForIdle()
        assertEquals(center, button.fetchSemanticsNode().boundsInRoot.center)
        assertTrue(button.fetchSemanticsNode().boundsInRoot.width >= restingBounds.width * 1.7f)
        compose.runOnIdle { listening.value = false; processing.value = true }
        compose.waitForIdle()
        assertEquals(center, button.fetchSemanticsNode().boundsInRoot.center)
        assertEquals(restingBounds.width, button.fetchSemanticsNode().boundsInRoot.width, 0.1f)
        button.assertIsNotEnabled()
    }

    @Test fun holdingStartsAndReleasingStopsExactlyOnce() {
        var starts = 0
        var stops = 0
        compose.setContent {
            FrenchCoursesTheme {
                var listening by remember { mutableStateOf(false) }
                VoiceHoldButton(true, listening, false, { starts++; listening = true }, { stops++; listening = false }, {})
            }
        }
        val button = compose.onNodeWithContentDescription("Maintenir pour dicter les courses")
        button.performTouchInput { down(center) }
        compose.waitForIdle()
        assertEquals(1, starts)
        assertEquals(0, stops)
        button.performTouchInput { advanceEventTime(1_000); up() }
        compose.waitForIdle()
        assertEquals(1, starts)
        assertEquals(1, stops)
    }

    @Test fun invalidQuantityBlocksConfirmationAndDeleteChangesOnlyDraft() {
        var confirmed = 0
        var latest = VoiceDraft(1, "pâtes", "500", "g")
        compose.setContent {
            FrenchCoursesTheme {
                var rows by remember { mutableStateOf(listOf(latest)) }
                VoiceReviewDialog(
                    VoiceUiState(transcript = "500 g de pâtes", draft = rows),
                    onEdit = { latest = it; rows = listOf(it) },
                    onDelete = { rows = emptyList() }, onDismiss = {}, onConfirm = { confirmed++ },
                )
            }
        }
        compose.onNodeWithText("500").performTextReplacement("0")
        compose.onNodeWithText("Ajouter à la liste de courses").assertIsNotEnabled()
        assertEquals("0", latest.quantity)
        assertEquals(0, confirmed)
        compose.onNodeWithText("Supprimer").performClick()
        compose.onNodeWithText("Ajouter à la liste de courses").assertIsNotEnabled()
        assertEquals(0, confirmed)
    }
}
