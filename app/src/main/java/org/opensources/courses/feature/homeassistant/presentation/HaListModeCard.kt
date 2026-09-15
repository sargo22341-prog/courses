package org.opensources.courses.feature.homeassistant.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.opensources.courses.R
import org.opensources.courses.core.designsystem.component.RadioRow
import org.opensources.courses.core.designsystem.component.SettingsCard
import org.opensources.courses.feature.homeassistant.domain.HaListMode

/**
 * Which Home Assistant lists reach the app. Leaving "all lists" removes the lists it imported from
 * this phone, so the user confirms first.
 */
@Composable
fun HaListModeCard(
    mode: HaListMode,
    importedListCount: Int,
    onModeChange: (HaListMode) -> Unit,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    SettingsCard(stringResource(R.string.ha_list_mode)) {
        Column(Modifier.selectableGroup()) {
            ModeOption(stringResource(R.string.ha_mode_app), stringResource(R.string.ha_mode_app_hint), mode == HaListMode.APP_CREATED_ONLY) {
                if (importedListCount > 0) confirming = true else onModeChange(HaListMode.APP_CREATED_ONLY)
            }
            ModeOption(stringResource(R.string.ha_mode_all), stringResource(R.string.ha_mode_all_hint), mode == HaListMode.ALL_LISTS) {
                onModeChange(HaListMode.ALL_LISTS)
            }
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.ha_mode_app_confirm_title)) },
            text = { Text(LocalResources.current.getQuantityString(R.plurals.ha_mode_app_confirm_body, importedListCount, importedListCount)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onModeChange(HaListMode.APP_CREATED_ONLY)
                    },
                ) { Text(stringResource(R.string.ha_mode_app_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ModeOption(
    label: String,
    hint: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Column {
        // Choosing the current mode again changes nothing.
        RadioRow(label, selected, onClick = { if (!selected) onSelect() })
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = HINT_INDENT),
        )
    }
}

/** Aligns the hint with the label, after the radio button and its spacing. */
private val HINT_INDENT = 36.dp
