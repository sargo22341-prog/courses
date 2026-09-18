package org.opensources.courses.core.designsystem.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.lerp

/**
 * The palette, sliding from the previous one when the theme changes (light, dark, following the
 * system) rather than switching at once. The first palette is shown as it is. A theme changed during
 * a slide starts from the colours on screen, so nothing jumps.
 */
@Composable
internal fun animatedColorScheme(target: ColorScheme): ColorScheme {
    val progress = remember { Animatable(1f) }
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    LaunchedEffect(target) {
        if (target == to) return@LaunchedEffect
        from = from.lerp(to, progress.value)
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, tween(THEME_TRANSITION_MILLIS, easing = FastOutSlowInEasing))
    }
    val fraction = progress.value
    return if (fraction >= 1f) to else from.lerp(to, fraction)
}

private fun ColorScheme.lerp(
    other: ColorScheme,
    fraction: Float,
): ColorScheme =
    copy(
        primary = lerp(primary, other.primary, fraction),
        onPrimary = lerp(onPrimary, other.onPrimary, fraction),
        primaryContainer = lerp(primaryContainer, other.primaryContainer, fraction),
        onPrimaryContainer = lerp(onPrimaryContainer, other.onPrimaryContainer, fraction),
        inversePrimary = lerp(inversePrimary, other.inversePrimary, fraction),
        secondary = lerp(secondary, other.secondary, fraction),
        onSecondary = lerp(onSecondary, other.onSecondary, fraction),
        secondaryContainer = lerp(secondaryContainer, other.secondaryContainer, fraction),
        onSecondaryContainer = lerp(onSecondaryContainer, other.onSecondaryContainer, fraction),
        tertiary = lerp(tertiary, other.tertiary, fraction),
        onTertiary = lerp(onTertiary, other.onTertiary, fraction),
        tertiaryContainer = lerp(tertiaryContainer, other.tertiaryContainer, fraction),
        onTertiaryContainer = lerp(onTertiaryContainer, other.onTertiaryContainer, fraction),
        background = lerp(background, other.background, fraction),
        onBackground = lerp(onBackground, other.onBackground, fraction),
        surface = lerp(surface, other.surface, fraction),
        onSurface = lerp(onSurface, other.onSurface, fraction),
        surfaceVariant = lerp(surfaceVariant, other.surfaceVariant, fraction),
        onSurfaceVariant = lerp(onSurfaceVariant, other.onSurfaceVariant, fraction),
        surfaceTint = lerp(surfaceTint, other.surfaceTint, fraction),
        inverseSurface = lerp(inverseSurface, other.inverseSurface, fraction),
        inverseOnSurface = lerp(inverseOnSurface, other.inverseOnSurface, fraction),
        error = lerp(error, other.error, fraction),
        onError = lerp(onError, other.onError, fraction),
        errorContainer = lerp(errorContainer, other.errorContainer, fraction),
        onErrorContainer = lerp(onErrorContainer, other.onErrorContainer, fraction),
        outline = lerp(outline, other.outline, fraction),
        outlineVariant = lerp(outlineVariant, other.outlineVariant, fraction),
        scrim = lerp(scrim, other.scrim, fraction),
        surfaceBright = lerp(surfaceBright, other.surfaceBright, fraction),
        surfaceDim = lerp(surfaceDim, other.surfaceDim, fraction),
        surfaceContainer = lerp(surfaceContainer, other.surfaceContainer, fraction),
        surfaceContainerHigh = lerp(surfaceContainerHigh, other.surfaceContainerHigh, fraction),
        surfaceContainerHighest = lerp(surfaceContainerHighest, other.surfaceContainerHighest, fraction),
        surfaceContainerLow = lerp(surfaceContainerLow, other.surfaceContainerLow, fraction),
        surfaceContainerLowest = lerp(surfaceContainerLowest, other.surfaceContainerLowest, fraction),
    )

private const val THEME_TRANSITION_MILLIS = 400
