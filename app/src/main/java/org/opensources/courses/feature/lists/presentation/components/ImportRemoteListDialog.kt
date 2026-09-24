package org.opensources.courses.feature.lists.presentation.components

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.opensources.courses.R
import org.opensources.courses.core.designsystem.component.StatusText
import org.opensources.courses.core.sync.RemoteListChoice
import org.opensources.courses.core.sync.SyncFailure
import org.opensources.courses.feature.lists.presentation.ListImportUiState

/** Chooses the Home Assistant list to bring into the app, linked, instead of creating an empty list. */
@Composable
fun ImportRemoteListDialog(
    state: ListImportUiState,
    onImport: (RemoteListChoice) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lists_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.lists_import_intro), style = MaterialTheme.typography.bodyMedium)
                when (state) {
                    ListImportUiState.Closed -> Unit
                    ListImportUiState.Loading -> StatusText(stringResource(R.string.ha_picker_loading), isError = false)
                    is ListImportUiState.Failed -> StatusText(stringResource(failureMessage(state.reason)), isError = true)
                    is ListImportUiState.Choosing ->
                        if (state.lists.isEmpty()) {
                            StatusText(stringResource(R.string.lists_import_empty), isError = false)
                        } else {
                            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                                items(state.lists, key = { it.remoteId }) { list -> RemoteListRow(list, onClick = { onImport(list) }) }
                            }
                        }
                }
            }
        },
        confirmButton = {
            if (state is ListImportUiState.Failed) TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun RemoteListRow(
    list: RemoteListChoice,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(list.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(list.remoteId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@StringRes
private fun failureMessage(reason: SyncFailure): Int =
    when (reason) {
        SyncFailure.UNREACHABLE -> R.string.lists_import_error_unreachable
        SyncFailure.UNAUTHORIZED -> R.string.lists_import_error_unauthorized
        SyncFailure.PROTOCOL, SyncFailure.LIST_UNAVAILABLE, SyncFailure.REJECTED -> R.string.lists_import_error
    }
