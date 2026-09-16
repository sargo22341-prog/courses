package org.opensources.courses.feature.homeassistant.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.opensources.courses.R
import org.opensources.courses.core.designsystem.component.SettingsCard
import org.opensources.courses.core.designsystem.component.StatusText
import org.opensources.courses.core.designsystem.component.SwitchRow

/**
 * Once an address and a token are saved, only "Connecté" and the synchronisation switch are shown;
 * tapping "Connecté" unfolds the connection settings, where the connection can also be forgotten.
 */
@Composable
fun HaConnectionCard(
    state: HaSettingsUiState,
    url: String,
    token: String,
    onUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onScanToken: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onForget: () -> Unit,
) {
    val configured = state.config.isConfigured
    // Unfolded by default only when nothing was saved yet, so a first setup is not folded away
    // (and its test button hidden) as soon as it is saved.
    var expanded by rememberSaveable { mutableStateOf(!configured) }
    SettingsCard(stringResource(R.string.ha_connection)) {
        if (configured) ConnectedRow(state.config.baseUrl, expanded, onToggle = { expanded = !expanded })
        if (configured && state.config.usesCleartext) CleartextWarning()
        SwitchRow(stringResource(R.string.ha_enabled), state.config.enabled, onEnabledChange)
        if (expanded || !configured) {
            ConnectionFields(state, url, token, onUrlChange, onTokenChange, onScanToken, onSave, onTest)
            if (configured) ForgetConnectionButton(onForget)
        }
        (state.connection as? HaActionStatus.Done)?.let { StatusText(stringResource(it.message.text), it.message.isError) }
    }
}

@Composable
private fun ConnectedRow(
    url: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onToggle).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.ha_connected), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = stringResource(if (expanded) R.string.ha_connection_collapse else R.string.ha_connection_expand),
        )
    }
}

@Composable
private fun ConnectionFields(
    state: HaSettingsUiState,
    url: String,
    token: String,
    onUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onScanToken: () -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text(stringResource(R.string.ha_url)) },
            placeholder = { Text(stringResource(R.string.ha_url_placeholder)) },
            singleLine = true,
            // Keyboards "correct" host names (ha.nas.home → ha.nas.homme): an address is never prose.
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = token,
            onValueChange = onTokenChange,
            label = { Text(stringResource(R.string.ha_token)) },
            placeholder = { if (state.config.hasToken) Text(stringResource(R.string.ha_token_saved)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = onScanToken) {
            Icon(painterResource(R.drawable.ic_qr_code), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.ha_scan_token))
        }
        Text(
            text = stringResource(R.string.ha_scan_token_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onSave) { Text(stringResource(R.string.ha_save)) }
            OutlinedButton(onClick = onTest, enabled = state.connection != HaActionStatus.Running) {
                Text(stringResource(if (state.connection == HaActionStatus.Running) R.string.ha_testing else R.string.ha_test))
            }
        }
    }
}
