package org.opensources.courses.feature.homeassistant.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.opensources.courses.R
import org.opensources.courses.core.designsystem.component.SettingsCard
import org.opensources.courses.core.designsystem.component.StatusText
import org.opensources.courses.core.designsystem.component.SwitchRow
import org.opensources.courses.feature.homeassistant.domain.HaListMode
import org.opensources.courses.feature.lists.domain.ShoppingList

@Composable
fun HaLinkedListsCard(
    state: HaSettingsUiState,
    onAutoCreateChange: (Boolean) -> Unit,
    onChoose: (String) -> Unit,
) {
    SettingsCard(stringResource(R.string.ha_lists_section)) {
        SwitchRow(stringResource(R.string.ha_auto_create_lists), state.config.autoCreateLists, onAutoCreateChange)
        Text(
            text = stringResource(if (state.config.autoCreateLists) R.string.ha_auto_create_on else R.string.ha_auto_create_off),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.lists.forEach { list ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(list.name, style = MaterialTheme.typography.bodyLarge)
                    Text(linkLabel(list, state), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { onChoose(list.id) }) { Text(stringResource(R.string.ha_list_choose)) }
            }
        }
        Text(stringResource(R.string.ha_rename_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun linkLabel(
    list: ShoppingList,
    state: HaSettingsUiState,
): String =
    when {
        list.remoteId != null -> stringResource(R.string.ha_list_linked, state.remoteName(list.remoteId))
        list.isSynchronized -> stringResource(R.string.ha_list_pending_creation)
        else -> stringResource(R.string.ha_list_not_synced)
    }

@Composable
fun HaListPickerDialog(
    list: ShoppingList,
    state: HaSettingsUiState,
    onLink: (String) -> Unit,
    onCreate: () -> Unit,
    onUnlink: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ha_picker_title, list.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.isSetupPicker) {
                    Text(stringResource(R.string.ha_setup_intro), style = MaterialTheme.typography.bodyMedium)
                }
                when (val remote = state.remoteLists) {
                    RemoteListsState.Loading, RemoteListsState.NotLoaded -> StatusText(stringResource(R.string.ha_picker_loading), isError = false)
                    is RemoteListsState.Failed -> StatusText(stringResource(remote.message.text), isError = true)
                    is RemoteListsState.Loaded ->
                        if (state.pickerOptions.isEmpty()) {
                            val empty = if (state.config.listMode == HaListMode.APP_CREATED_ONLY) R.string.ha_picker_empty_app else R.string.ha_picker_empty_all
                            StatusText(stringResource(empty), isError = false)
                        } else {
                            LazyColumn(Modifier.heightIn(max = 280.dp)) {
                                items(state.pickerOptions, key = { it.entityId }) { option ->
                                    PickerRow(
                                        label = option.name,
                                        detail = option.entityId,
                                        selected = option.entityId == list.remoteId,
                                        onClick = { onLink(option.entityId) },
                                    )
                                }
                            }
                        }
                }
                PickerRow(label = stringResource(R.string.ha_picker_create, list.name), detail = null, selected = false, onClick = onCreate)
                PickerRow(label = stringResource(R.string.ha_picker_none), detail = null, selected = !list.isSynchronized, onClick = onUnlink)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(if (state.isSetupPicker) R.string.ha_setup_later else R.string.action_cancel)) }
        },
    )
}

@Composable
private fun PickerRow(
    label: String,
    detail: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
