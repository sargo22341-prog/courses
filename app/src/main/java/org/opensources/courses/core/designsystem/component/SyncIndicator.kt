package org.opensources.courses.core.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.opensources.courses.R
import org.opensources.courses.core.designsystem.theme.offline
import org.opensources.courses.core.sync.SyncFailure
import org.opensources.courses.core.sync.SyncSnapshot
import org.opensources.courses.core.sync.SyncState

/**
 * Discreet status line, under the list title and in the settings. Without Home Assistant only "offline" is shown:
 * "synchronised" would be meaningless for a purely local list.
 */
@Composable
fun SyncIndicator(snapshot: SyncSnapshot) {
    if (!snapshot.remoteEnabled && snapshot.state != SyncState.OFFLINE) return
    val (color, label) =
        when (snapshot.state) {
            SyncState.OFFLINE -> MaterialTheme.colorScheme.offline to stringResource(R.string.sync_offline)
            SyncState.SYNCING -> MaterialTheme.colorScheme.primary to stringResource(R.string.sync_syncing)
            SyncState.SYNC_ERROR -> MaterialTheme.colorScheme.error to stringResource(errorLabel(snapshot.failure))
            SyncState.ONLINE -> MaterialTheme.colorScheme.tertiary to stringResource(R.string.sync_synced)
        }
    val pending =
        if (snapshot.remoteEnabled && snapshot.pendingCount > 0 && snapshot.state != SyncState.SYNCING) {
            " · " + LocalResources.current.getQuantityString(R.plurals.sync_pending, snapshot.pendingCount, snapshot.pendingCount)
        } else {
            ""
        }
    val dotColor by animateColorAsState(color, label = "sync_color")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Crossfade(snapshot.state == SyncState.SYNCING, label = "sync_dot") { syncing ->
            if (syncing) {
                CircularProgressIndicator(modifier = Modifier.size(8.dp), strokeWidth = 1.5.dp, color = dotColor)
            } else {
                StatusDot(dotColor, pulsing = pending.isNotEmpty())
            }
        }
        Spacer(Modifier.width(6.dp))
        AnimatedContent(
            targetState = label + pending,
            transitionSpec = { fadeIn(tween(TEXT_FADE_MILLIS)) togetherWith fadeOut(tween(TEXT_FADE_MILLIS)) using SizeTransform(clip = false) },
            label = "sync_label",
        ) { text ->
            Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Breathes slowly while changes wait to be sent: something is still to happen. */
@Composable
private fun StatusDot(
    color: Color,
    pulsing: Boolean,
) {
    val alpha =
        if (pulsing) {
            rememberInfiniteTransition(label = "sync_pulse")
                .animateFloat(
                    initialValue = 1f,
                    targetValue = PULSE_MIN_ALPHA,
                    animationSpec = infiniteRepeatable(tween(PULSE_MILLIS), RepeatMode.Reverse),
                    label = "sync_pulse_alpha",
                ).value
        } else {
            1f
        }
    Box(Modifier.size(8.dp).graphicsLayer { this.alpha = alpha }.background(color, CircleShape))
}

private const val TEXT_FADE_MILLIS = 200
private const val PULSE_MILLIS = 900
private const val PULSE_MIN_ALPHA = 0.35f

@StringRes
private fun errorLabel(failure: SyncFailure?): Int =
    when (failure) {
        SyncFailure.LIST_UNAVAILABLE -> R.string.sync_list_unavailable
        SyncFailure.REJECTED -> R.string.sync_changes_rejected
        else -> R.string.sync_error
    }
