package org.opensources.courses.feature.voice.presentation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp

/** The animation exists only during a press or listening, and never changes the FAB bounds. */
@Composable
internal fun VoiceListeningHalo() {
    val transition = rememberInfiniteTransition(label = "listeningHalo")
    val pulse by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "haloPulse",
    )
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Box(Modifier.size(56.dp).drawBehind {
        val radius = size.minDimension / 2
        drawCircle(tertiary.copy(alpha = 0.14f + pulse * 0.08f), radius * (3f + pulse * 0.5f))
        drawCircle(primary.copy(alpha = 0.20f + pulse * 0.10f), radius * (2.4f + pulse * 0.3f))
    })
}
