package org.opensources.courses.feature.shopping.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Behind a row being swiped: grey until the swipe goes far enough to delete, then red with a larger
 * bin, and a tick in the hand when that point is reached, so the user knows before letting go.
 */
@Composable
internal fun SwipeDeleteBackground(state: SwipeToDismissBoxState) {
    val armed = state.targetValue == SwipeToDismissBoxValue.EndToStart
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state) {
        snapshotFlow { state.targetValue == SwipeToDismissBoxValue.EndToStart }
            .drop(1)
            .filter { crossed -> crossed }
            .collect { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) }
    }
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(if (armed) colors.errorContainer else colors.surfaceContainerHighest, label = "swipe_background")
    val tint by animateColorAsState(if (armed) colors.onErrorContainer else colors.onSurfaceVariant, label = "swipe_icon")
    val scale by animateFloatAsState(
        targetValue = if (armed) ARMED_ICON_SCALE else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "swipe_scale",
    )
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(background, MaterialTheme.shapes.medium)
                .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(
            Icons.Filled.Delete,
            contentDescription = null,
            tint = tint,
            modifier =
                Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}

/**
 * 1 when [highlighted] turns true, fading to 0. The fade runs apart from the effect: the highlight is
 * cleared as soon as it is shown, and must still fade out rather than stop.
 */
@Composable
internal fun rememberHighlight(highlighted: Boolean): State<Float> {
    val glow = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(highlighted) {
        if (highlighted) {
            scope.launch {
                glow.snapTo(HIGHLIGHT_START)
                glow.animateTo(0f, tween(HIGHLIGHT_MILLIS, delayMillis = HIGHLIGHT_HOLD_MILLIS))
            }
        }
    }
    return glow.asState()
}

/** A small bounce of the checkbox when [checked] changes, never when the row first appears. */
@Composable
internal fun rememberCheckBounce(checked: Boolean): Animatable<Float, AnimationVector1D> {
    val scale = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(checked) }
    LaunchedEffect(checked) {
        if (checked == previous) return@LaunchedEffect
        previous = checked
        scale.snapTo(BOUNCE_START)
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }
    return scale
}

private const val ARMED_ICON_SCALE = 1.3f
private const val HIGHLIGHT_START = 0.9f
private const val HIGHLIGHT_MILLIS = 900
private const val HIGHLIGHT_HOLD_MILLIS = 350
private const val BOUNCE_START = 0.75f
