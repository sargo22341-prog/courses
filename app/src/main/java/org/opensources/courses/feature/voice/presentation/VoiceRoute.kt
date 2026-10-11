package org.opensources.courses.feature.voice.presentation

import android.Manifest
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.opensources.courses.R
import org.opensources.courses.core.permission.PermissionResult
import org.opensources.courses.core.permission.isPermissionGranted
import org.opensources.courses.core.permission.openApplicationSettings
import org.opensources.courses.core.permission.rememberPermissionRequester
import org.opensources.courses.feature.voice.domain.VoiceRecognition
import org.opensources.courses.feature.voice.presentation.components.VoiceReviewDialog
import org.opensources.courses.feature.voice.presentation.components.VoiceHoldButton

@Composable
fun VoiceRoute(listId: String?, viewModel: VoiceViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionExplanation by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    val requestPermission = rememberPermissionRequester(Manifest.permission.RECORD_AUDIO) { result ->
        when (result) {
            PermissionResult.GRANTED -> permissionExplanation = false
            PermissionResult.DENIED -> permissionExplanation = true
            PermissionResult.DENIED_PERMANENTLY -> { permanentlyDenied = true; permissionExplanation = true }
        }
    }
    CancelVoiceOnBackground(viewModel)
    VoiceHoldButton(
        enabled = listId != null && state.draft == null,
        listening = state.recognition == VoiceRecognition.Listening,
        processing = state.recognition == VoiceRecognition.Processing,
        onStart = {
            if (context.isPermissionGranted(Manifest.permission.RECORD_AUDIO)) listId?.let(viewModel::start)
            else permissionExplanation = true
        },
        onStop = viewModel::stop,
        onCancel = viewModel::cancelRecording,
    )
    state.draft?.let { VoiceReviewDialog(state, viewModel::edit, viewModel::delete, viewModel::dismiss, viewModel::confirm) }
    if (permissionExplanation) {
        VoiceMessageDialog(R.string.voice_permission, { permissionExplanation = false }) {
            permissionExplanation = false
            if (permanentlyDenied) context.openApplicationSettings() else requestPermission()
        }
    }
    val failure = state.recognition as? VoiceRecognition.Failure
    if (failure != null) {
        VoiceMessageDialog(if (failure.unavailable) R.string.voice_unavailable else R.string.voice_error, viewModel::dismiss, viewModel::dismiss)
    }
}

@Composable
private fun CancelVoiceOnBackground(viewModel: VoiceViewModel) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.cancelRecording()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); viewModel.cancelRecording() }
    }
}

@Composable
private fun VoiceMessageDialog(message: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.voice_review)) },
        text = { Text(stringResource(message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.voice_continue)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
