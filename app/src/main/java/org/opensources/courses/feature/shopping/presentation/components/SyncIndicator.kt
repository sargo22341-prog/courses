package org.opensources.courses.feature.shopping.presentation.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.opensources.courses.R
import org.opensources.courses.core.sync.SyncFailure
import org.opensources.courses.core.sync.SyncSnapshot
import org.opensources.courses.core.sync.SyncState

private val OfflineColor = Color(0xFFE0973A)

/**
 * Discreet status line under the list title. Without Home Assistant only "offline" is shown:
 * "synchronised" would be meaningless for a purely local list.
 */
@Composable
fun SyncIndicator(snapshot: SyncSnapshot) {
    if (!snapshot.remoteEnabled && snapshot.state != SyncState.OFFLINE) return
    val (color, label) =
        when (snapshot.state) {
            SyncState.OFFLINE -> OfflineColor to stringResource(R.string.sync_offline)
            SyncState.SYNCING -> MaterialTheme.colorScheme.primary to stringResource(R.string.sync_syncing)
            SyncState.SYNC_ERROR ->
                MaterialTheme.colorScheme.error to
                    stringResource(if (snapshot.failure == SyncFailure.LIST_UNAVAILABLE) R.string.sync_list_unavailable else R.string.sync_error)
            SyncState.ONLINE -> MaterialTheme.colorScheme.tertiary to stringResource(R.string.sync_synced)
        }
    val pending =
        if (snapshot.remoteEnabled && snapshot.pendingCount > 0 && snapshot.state != SyncState.SYNCING) {
            " · " + LocalResources.current.getQuantityString(R.plurals.sync_pending, snapshot.pendingCount, snapshot.pendingCount)
        } else {
            ""
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (snapshot.state == SyncState.SYNCING) {
            CircularProgressIndicator(modifier = Modifier.size(8.dp), strokeWidth = 1.5.dp, color = color)
        } else {
            Box(Modifier.size(8.dp).background(color, CircleShape))
        }
        Spacer(Modifier.width(6.dp))
        Text(label + pending, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
