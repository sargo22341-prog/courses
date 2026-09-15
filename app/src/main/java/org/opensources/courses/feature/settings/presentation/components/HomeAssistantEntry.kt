package org.opensources.courses.feature.settings.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.opensources.courses.R
import org.opensources.courses.core.designsystem.component.SettingsCard
import org.opensources.courses.core.designsystem.component.SyncIndicator
import org.opensources.courses.feature.settings.presentation.HomeAssistantStatus

/** Entry to the Home Assistant screen: connection, then synchronisation state, then address. */
@Composable
fun HomeAssistantEntry(
    status: HomeAssistantStatus,
    onOpen: () -> Unit,
) {
    SettingsCard(stringResource(R.string.settings_home_assistant)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onOpen).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                when (status) {
                    HomeAssistantStatus.NotConfigured ->
                        Text(stringResource(R.string.settings_home_assistant_off), style = MaterialTheme.typography.bodyLarge)
                    is HomeAssistantStatus.Connected -> ConnectedStatus(status)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun ConnectedStatus(status: HomeAssistantStatus.Connected) {
    Text(stringResource(R.string.ha_connected), style = MaterialTheme.typography.bodyLarge)
    if (status.sync != null) {
        SyncIndicator(status.sync)
    } else {
        Text(
            text = stringResource(R.string.settings_home_assistant_sync_disabled),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        text = status.baseUrl,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
