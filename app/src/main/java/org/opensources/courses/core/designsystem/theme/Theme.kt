package org.opensources.courses.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import org.opensources.courses.feature.settings.domain.ThemeMode

private val CoursesShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )

/** Palette of the light or dark theme, also used to preview a theme other than the current one. */
fun themeColorScheme(dark: Boolean): ColorScheme = if (dark) DarkColors else LightColors

@Composable
fun CoursesTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark =
        when (themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
        }
    SystemBarsAppearance(darkTheme = dark)
    MaterialTheme(
        colorScheme = themeColorScheme(dark),
        typography = CoursesTypography,
        shapes = CoursesShapes,
        content = content,
    )
}
