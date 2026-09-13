package org.opensources.courses.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Light: calm, warm paper-like surfaces, a muted clay accent and almost no borders.
 * Dark: sober graphite surfaces separated by small luminance steps, a discreet blue-grey accent.
 * Both palettes are original; they only borrow the general mood of modern AI tool interfaces.
 */
internal val LightColors =
    lightColorScheme(
        primary = Color(0xFFA85A3C),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFF1DED3),
        onPrimaryContainer = Color(0xFF3E1A0C),
        secondary = Color(0xFF6D6559),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFECE5D8),
        onSecondaryContainer = Color(0xFF2A251E),
        tertiary = Color(0xFF4F7157),
        onTertiary = Color(0xFFFFFFFF),
        background = Color(0xFFF8F5EF),
        onBackground = Color(0xFF2A2825),
        surface = Color(0xFFF8F5EF),
        onSurface = Color(0xFF2A2825),
        surfaceVariant = Color(0xFFEDE8DE),
        onSurfaceVariant = Color(0xFF6C675F),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF4F0E8),
        surfaceContainer = Color(0xFFEFEAE0),
        surfaceContainerHigh = Color(0xFFE9E3D8),
        surfaceContainerHighest = Color(0xFFE3DCD0),
        outline = Color(0xFFC9C1B4),
        outlineVariant = Color(0xFFE2DBCF),
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFF9DEDC),
        onErrorContainer = Color(0xFF410E0B),
    )

internal val DarkColors =
    darkColorScheme(
        primary = Color(0xFFA9C1EC),
        onPrimary = Color(0xFF10223F),
        primaryContainer = Color(0xFF263449),
        onPrimaryContainer = Color(0xFFDCE6F8),
        secondary = Color(0xFFB9BDC4),
        onSecondary = Color(0xFF202327),
        secondaryContainer = Color(0xFF2C2F34),
        onSecondaryContainer = Color(0xFFE1E3E7),
        tertiary = Color(0xFF9FCDA9),
        onTertiary = Color(0xFF0F2A17),
        background = Color(0xFF111213),
        onBackground = Color(0xFFEAEAEA),
        surface = Color(0xFF111213),
        onSurface = Color(0xFFEAEAEA),
        surfaceVariant = Color(0xFF24262A),
        onSurfaceVariant = Color(0xFFA3A6AB),
        surfaceContainerLowest = Color(0xFF0B0C0D),
        surfaceContainerLow = Color(0xFF17181A),
        surfaceContainer = Color(0xFF1C1D20),
        surfaceContainerHigh = Color(0xFF232427),
        surfaceContainerHighest = Color(0xFF2B2D30),
        outline = Color(0xFF45484D),
        outlineVariant = Color(0xFF2E3034),
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFF9DEDC),
    )
