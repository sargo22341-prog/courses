package org.opensources.courses.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePaletteTest {
    @Test
    fun `the dark palette is recognised, even with other roles changed`() {
        assertTrue(themeColorScheme(dark = true).isDark)
        assertTrue(themeColorScheme(dark = true).copy(primary = Color.Red).isDark)
    }

    @Test
    fun `the light palette is not dark`() {
        assertFalse(themeColorScheme(dark = false).isDark)
    }

    @Test
    fun `each palette has its own offline colour`() {
        assertTrue(themeColorScheme(dark = true).offline != themeColorScheme(dark = false).offline)
    }
}
