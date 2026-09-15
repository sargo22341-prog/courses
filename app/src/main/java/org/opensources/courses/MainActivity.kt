package org.opensources.courses

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import org.opensources.courses.core.designsystem.theme.CoursesTheme
import org.opensources.courses.navigation.CoursesNavHost

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LifecycleStartEffect(viewModel) {
                viewModel.onAppStarted()
                onStopOrDispose { viewModel.onAppStopped() }
            }
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            when (val current = state) {
                // Preferences are read from disk in a few milliseconds; the window background
                // (same colour as the app) is shown meanwhile, never a network loading screen.
                MainUiState.Loading -> CoursesTheme { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) }
                is MainUiState.Ready ->
                    CoursesTheme(themeMode = current.themeMode) {
                        CoursesNavHost(showWelcome = !current.onboardingCompleted)
                    }
            }
        }
    }
}
