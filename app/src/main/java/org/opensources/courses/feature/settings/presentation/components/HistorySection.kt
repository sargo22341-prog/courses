package org.opensources.courses.feature.settings.presentation.components

import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.opensources.courses.R
import org.opensources.courses.core.designsystem.component.ConfirmDialog
import org.opensources.courses.core.designsystem.component.SettingsCard
import org.opensources.courses.core.designsystem.component.StatusText
import org.opensources.courses.core.designsystem.component.SwitchRow
import org.opensources.courses.feature.settings.presentation.HistorySettings

/** [onClear] is only called once the user confirmed. */
@Composable
fun HistorySection(
    history: HistorySettings,
    onEnabledChange: (Boolean) -> Unit,
    onClear: () -> Unit,
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    SettingsCard(stringResource(R.string.history_title)) {
        SwitchRow(stringResource(R.string.settings_history_enabled), history.enabled, onEnabledChange)
        Text(
            text = stringResource(R.string.settings_history_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(onClick = { confirmClear = true }, enabled = history.hasHistory) {
            Text(stringResource(R.string.settings_history_clear))
        }
        if (!history.hasHistory) StatusText(stringResource(R.string.settings_history_empty), isError = false)
    }
    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.settings_history_clear_title),
            text = stringResource(R.string.settings_history_clear_body),
            confirmLabel = stringResource(R.string.settings_history_clear_confirm),
            onConfirm = {
                confirmClear = false
                onClear()
            },
            onDismiss = { confirmClear = false },
        )
    }
}
