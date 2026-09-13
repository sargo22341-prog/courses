package org.opensources.courses.feature.homeassistant.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.opensources.courses.R
import org.opensources.courses.core.designsystem.component.SettingsCard
import org.opensources.courses.core.designsystem.component.StatusText
import org.opensources.courses.core.designsystem.component.SwitchRow

@Composable
fun HaConnectionCard(
    state: HaSettingsUiState,
    url: String,
    token: String,
    onUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
) {
    SettingsCard(stringResource(R.string.ha_connection)) {
        SwitchRow(stringResource(R.string.ha_enabled), state.config.enabled, onEnabledChange)
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text(stringResource(R.string.ha_url)) },
            placeholder = { Text(stringResource(R.string.ha_url_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onSave) { Text(stringResource(R.string.ha_save)) }
            OutlinedButton(onClick = onTest, enabled = state.connection != HaActionStatus.Running) {
                Text(stringResource(if (state.connection == HaActionStatus.Running) R.string.ha_testing else R.string.ha_test))
            }
        }
        (state.connection as? HaActionStatus.Done)?.let { StatusText(stringResource(it.message.text), it.message.isError) }
    }
}
