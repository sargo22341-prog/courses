package org.opensources.courses.feature.shopping.presentation

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.courses.feature.catalog.domain.ProductSuggestion
import org.opensources.courses.feature.shopping.presentation.components.ADD_ITEM_FIELD_TAG
import org.opensources.courses.feature.shopping.presentation.components.HISTORY_PANEL_TAG
import org.opensources.courses.testing.FrenchCoursesTheme

/** Runs with the real on-screen keyboard of the device, drawn edge to edge like the app. */
@RunWith(AndroidJUnit4::class)
class HistoryKeyboardTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun closingTheKeyboardHidesTheHistory() {
        composeRule.activity.runOnUiThread { composeRule.activity.enableEdgeToEdge() }
        val state = ShoppingUiState(isLoading = false, listName = "Courses", history = listOf(ProductSuggestion("seed:beurre", "Beurre", null)))
        composeRule.setContent { FrenchCoursesTheme { ShoppingScreen(state, query = "", actions = ShoppingActions()) } }

        composeRule.onNodeWithTag(ADD_ITEM_FIELD_TAG).performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) { keyboardVisible() }
        composeRule.onNodeWithTag(HISTORY_PANEL_TAG).assertExists()

        composeRule.activity.runOnUiThread {
            val window = composeRule.activity.window
            WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
        }

        composeRule.waitUntil(TIMEOUT_MILLIS) { composeRule.onAllNodesWithTag(HISTORY_PANEL_TAG).fetchSemanticsNodes().isEmpty() }
    }

    private fun keyboardVisible(): Boolean {
        var visible = false
        composeRule.runOnUiThread {
            val insets = ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView)
            visible = insets?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        return visible
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
