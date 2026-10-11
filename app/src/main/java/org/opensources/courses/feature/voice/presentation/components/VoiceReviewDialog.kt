package org.opensources.courses.feature.voice.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.opensources.courses.R
import org.opensources.courses.feature.voice.presentation.VoiceDraft
import org.opensources.courses.feature.voice.presentation.VoiceUiState

@Composable
fun VoiceReviewDialog(state: VoiceUiState, onEdit: (VoiceDraft) -> Unit, onDelete: (Int) -> Unit, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val rows = state.draft ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.voice_review)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text(state.transcript.replace("[unk]", stringResource(R.string.voice_unknown))) }
                item { Text(stringResource(R.string.voice_check)) }
                if (state.importFailed) item { Text(stringResource(R.string.voice_import_error)) }
                items(rows, key = { it.id }) { row -> VoiceDraftRow(row, !state.importing, onEdit, onDelete) }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !state.importing && rows.isNotEmpty() && rows.all { it.valid }) {
                Text(stringResource(R.string.voice_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.importing) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun VoiceDraftRow(row: VoiceDraft, enabled: Boolean, onEdit: (VoiceDraft) -> Unit, onDelete: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = row.name.replace("[unk]", stringResource(R.string.voice_unknown)), onValueChange = { onEdit(row.copy(name = it.take(200))) },
            label = { Text(stringResource(R.string.shopping_edit_name)) }, singleLine = true, enabled = enabled,
            isError = row.name.isBlank() || row.name.contains("[unk]"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = row.quantity, onValueChange = { onEdit(row.copy(quantity = it.take(12))) },
                label = { Text(stringResource(R.string.shopping_edit_quantity)) }, singleLine = true,
                enabled = enabled, isError = !row.valid, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedTextField(
                value = row.unit, onValueChange = { onEdit(row.copy(unit = it.take(30))) },
                label = { Text(stringResource(R.string.shopping_edit_unit)) }, singleLine = true,
                enabled = enabled, modifier = Modifier.weight(1f),
            )
        }
        TextButton(onClick = { onDelete(row.id) }, enabled = enabled) { Text(stringResource(R.string.action_delete)) }
    }
}
