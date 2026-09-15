package org.opensources.courses.feature.settings.presentation

import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.courses.core.designsystem.theme.CoursesTheme
import org.opensources.courses.feature.settings.domain.ThemeMode
import org.opensources.courses.feature.settings.presentation.components.ThemeModeSelector

@RunWith(AndroidJUnit4::class)
class ThemeModeSelectorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun threeThemeChoicesOnOneRow() {
        var chosen: ThemeMode? = null
        composeRule.setContent { CoursesTheme { ThemeModeSelector(selected = ThemeMode.SYSTEM, onSelect = { chosen = it }) } }

        composeRule.onNodeWithText("Système").assertIsSelected()
        composeRule.onNodeWithText("Clair").assertIsNotSelected()
        val lightTop = composeRule.onNodeWithText("Clair").getBoundsInRoot().top
        assertEquals(lightTop, composeRule.onNodeWithText("Sombre").getBoundsInRoot().top)
        assertEquals(lightTop, composeRule.onNodeWithText("Système").getBoundsInRoot().top)
        // Compact buttons, still a full touch target.
        composeRule.onNodeWithText("Clair").assertHeightIsEqualTo(48.dp)
        composeRule.onNodeWithText("Système").assertHeightIsEqualTo(48.dp)

        composeRule.onNodeWithText("Sombre").performClick()

        assertEquals(ThemeMode.DARK, chosen)
    }
}
