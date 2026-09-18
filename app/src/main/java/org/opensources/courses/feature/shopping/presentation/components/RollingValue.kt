package org.opensources.courses.feature.shopping.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A number that rolls like a counter when it changes: up when it grows, down when it shrinks, so the
 * eye catches the change and its direction.
 */
@Composable
internal fun <T : Comparable<T>> RollingValue(
    value: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = value,
        modifier = modifier,
        transitionSpec = {
            val direction = if (targetState > initialState) 1 else -1
            (slideInVertically(tween(ROLL_MILLIS)) { height -> direction * height } + fadeIn(tween(ROLL_MILLIS)))
                .togetherWith(slideOutVertically(tween(ROLL_MILLIS)) { height -> -direction * height } + fadeOut(tween(ROLL_MILLIS)))
                .using(SizeTransform(clip = false))
        },
        label = "rolling_value",
    ) { shown -> content(shown) }
}

private const val ROLL_MILLIS = 220
