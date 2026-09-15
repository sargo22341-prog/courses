package org.opensources.courses.core.designsystem.theme

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.courses.feature.settings.domain.ThemeMode

/** Both modes are tested, so the result never depends on the dark mode of the test device. */
@RunWith(AndroidJUnit4::class)
class SystemBarsAppearanceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun forcedLightThemeDrawsDarkSystemBarIcons() = assertLightSystemBars(ThemeMode.LIGHT, expected = true)

    @Test
    fun forcedDarkThemeDrawsLightSystemBarIcons() = assertLightSystemBars(ThemeMode.DARK, expected = false)

    private fun assertLightSystemBars(
        mode: ThemeMode,
        expected: Boolean,
    ) {
        composeRule.setContent { CoursesTheme(themeMode = mode) {} }
        composeRule.waitForIdle()

        val window = composeRule.activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        assertEquals(expected, controller.isAppearanceLightStatusBars)
        assertEquals(expected, controller.isAppearanceLightNavigationBars)
    }
}
