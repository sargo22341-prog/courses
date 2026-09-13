package org.opensources.courses.core.permission

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

enum class PermissionResult {
    GRANTED,

    /** Refused, Android can still show its dialog. */
    DENIED,

    /** Refused for good: only the system settings can grant it now. */
    DENIED_PERMANENTLY,
}

fun Context.isPermissionGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** Re-evaluated on every resume, so a grant or revoke made from the system settings is picked up. */
@Composable
fun rememberPermissionGranted(permission: String): Boolean {
    val context = LocalContext.current
    var granted by remember(permission) { mutableStateOf(context.isPermissionGranted(permission)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, permission) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) granted = context.isPermissionGranted(permission)
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return granted
}

/** Returns a function showing the system permission dialog; [onResult] receives the outcome. */
@Composable
fun rememberPermissionRequester(
    permission: String,
    onResult: (PermissionResult) -> Unit,
): () -> Unit {
    val activity = LocalActivity.current
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            currentOnResult(
                when {
                    granted -> PermissionResult.GRANTED
                    activity?.shouldShowRequestPermissionRationale(permission) == true -> PermissionResult.DENIED
                    else -> PermissionResult.DENIED_PERMANENTLY
                },
            )
        }
    return remember(launcher, permission) { { launcher.launch(permission) } }
}

/** Opens this application's page in the system settings (permissions can be changed there). */
fun Context.openApplicationSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
