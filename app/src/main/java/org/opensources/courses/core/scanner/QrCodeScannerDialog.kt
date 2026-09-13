package org.opensources.courses.core.scanner

import android.Manifest
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.opensources.courses.R
import org.opensources.courses.core.permission.PermissionResult
import org.opensources.courses.core.permission.openApplicationSettings
import org.opensources.courses.core.permission.rememberPermissionGranted
import org.opensources.courses.core.permission.rememberPermissionRequester

/**
 * Full-screen QR code scanner using the device camera directly (CameraX + ZXing), so it works
 * without Google Play services. Asks for the CAMERA permission when needed and explains how to
 * grant it after a refusal. [onScanned] receives the raw decoded text.
 */
@Composable
fun QrCodeScannerDialog(
    onScanned: (String) -> Unit,
    onCameraUnavailable: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(color = Color.Black, contentColor = Color.White, modifier = Modifier.fillMaxSize()) {
            val cameraGranted = rememberPermissionGranted(Manifest.permission.CAMERA)
            var refusal by rememberSaveable { mutableStateOf<PermissionResult?>(null) }
            val requestCamera = rememberPermissionRequester(Manifest.permission.CAMERA) { result -> refusal = result }
            LaunchedEffect(Unit) { if (!cameraGranted) requestCamera() }

            Box(Modifier.fillMaxSize()) {
                when {
                    cameraGranted -> ScannerContent(onScanned, onCameraUnavailable)
                    refusal == PermissionResult.DENIED || refusal == PermissionResult.DENIED_PERMANENTLY ->
                        CameraPermissionExplanation(
                            permanentlyDenied = refusal == PermissionResult.DENIED_PERMANENTLY,
                            onRequest = requestCamera,
                            onCancel = onDismiss,
                        )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.statusBarsPadding().padding(8.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                }
            }
        }
    }
}

@Composable
private fun ScannerContent(
    onScanned: (String) -> Unit,
    onCameraUnavailable: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        CameraQrPreview(onDecoded = onScanned, onCameraError = onCameraUnavailable, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .align(Alignment.Center)
                .size(260.dp)
                .border(3.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(28.dp)),
        )
        Text(
            text = stringResource(R.string.qr_scanner_hint),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(32.dp),
        )
    }
}

@Composable
private fun CameraPermissionExplanation(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.qr_camera_permission_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.qr_camera_permission_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Button(
            onClick = { if (permanentlyDenied) context.openApplicationSettings() else onRequest() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (permanentlyDenied) R.string.open_app_settings else R.string.qr_camera_permission_grant))
        }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel), color = Color.White) }
    }
}
