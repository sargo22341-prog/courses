package org.opensources.courses.feature.homeassistant.presentation

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.opensources.courses.R
import org.opensources.courses.core.designsystem.component.SettingsCard
import org.opensources.courses.core.permission.PermissionResult
import org.opensources.courses.core.permission.openApplicationSettings
import org.opensources.courses.core.permission.rememberPermissionGranted
import org.opensources.courses.core.permission.rememberPermissionRequester

/**
 * Android 17 blocks connections to local-network addresses (homeassistant.local, ha.nas.home,
 * 192.168.x.x…) until the user grants this runtime permission, whatever the certificate. Shown on
 * the Home Assistant screen as long as it is missing.
 */
const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

@Composable
fun LocalNetworkPermissionCard() {
    if (rememberPermissionGranted(LOCAL_NETWORK_PERMISSION)) return
    val context = LocalContext.current
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    val request = rememberPermissionRequester(LOCAL_NETWORK_PERMISSION) { result -> permanentlyDenied = result == PermissionResult.DENIED_PERMANENTLY }
    SettingsCard(stringResource(R.string.local_network_title)) {
        Text(stringResource(R.string.local_network_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        Button(onClick = { if (permanentlyDenied) context.openApplicationSettings() else request() }) {
            Text(stringResource(if (permanentlyDenied) R.string.open_app_settings else R.string.local_network_grant))
        }
    }
}
