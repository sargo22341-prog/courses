package org.opensources.courses.testing

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import org.opensources.courses.core.designsystem.theme.CoursesTheme
import java.util.Locale

/**
 * UI tests look for French texts: the screen is shown in French whatever the language of the phone or
 * the language chosen for the app on it.
 */
@Composable
fun FrenchCoursesTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val french =
        remember(context, configuration) {
            context.createConfigurationContext(Configuration(configuration).apply { setLocale(Locale.FRENCH) })
        }
    CompositionLocalProvider(
        LocalContext provides french,
        LocalConfiguration provides french.resources.configuration,
        LocalResources provides french.resources,
    ) {
        CoursesTheme(content = content)
    }
}
