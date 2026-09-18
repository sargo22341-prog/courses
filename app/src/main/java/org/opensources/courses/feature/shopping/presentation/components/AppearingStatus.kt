package org.opensources.courses.feature.shopping.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A short status ("Your list is empty", "Everything is bought"): its icon pops in, then the text
 * rises into place. It plays each time the status appears, never while it stays.
 */
@Composable
internal fun AppearingStatus(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
) {
    val appeared = remember { MutableTransitionState(false) }.apply { targetState = true }
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedVisibility(appeared, enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn()) {
            // The text says it all: the icon is decorative.
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(40.dp))
        }
        AnimatedVisibility(
            appeared,
            enter = fadeIn(tween(TEXT_MILLIS, delayMillis = TEXT_DELAY_MILLIS)) + slideInVertically(tween(TEXT_MILLIS, delayMillis = TEXT_DELAY_MILLIS)) { it / 2 },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = title,
                    style = if (body == null) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
                    color = if (body == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                body?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

private const val TEXT_MILLIS = 300
private const val TEXT_DELAY_MILLIS = 120
