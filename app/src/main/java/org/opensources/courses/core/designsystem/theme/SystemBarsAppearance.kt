package org.opensources.courses.core.designsystem.theme

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * System bar icons (time, network, battery, navigation) follow the theme chosen in the app, not the
 * system one. The default edge-to-edge setup reads the system dark mode: with "Clair" forced on a
 * dark system the icons were drawn white on the light background, and black on a forced "Sombre".
 */
@Composable
internal fun SystemBarsAppearance(darkTheme: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    DisposableEffect(activity, darkTheme) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
            navigationBarStyle = SystemBarStyle.auto(LightNavigationScrim, DarkNavigationScrim) { darkTheme },
        )
        onDispose {}
    }
}

// Same scrims as androidx.activity's defaults, drawn by the system behind a three-button
// navigation bar: window colours, outside the Compose palette.
private val LightNavigationScrim = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
private val DarkNavigationScrim = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
