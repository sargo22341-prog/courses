package org.opensources.courses.feature.onboarding.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.opensources.courses.R
import org.opensources.courses.feature.language.domain.AppLanguage
import org.opensources.courses.feature.language.presentation.components.LanguageSelector

@Composable
fun WelcomeRoute(
    onStart: () -> Unit,
    onConnectHomeAssistant: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.exit) {
        when (state.exit) {
            WelcomeExit.SHOPPING -> onStart()
            WelcomeExit.HOME_ASSISTANT -> onConnectHomeAssistant()
            null -> Unit
        }
    }
    WelcomeScreen(
        language = state.language,
        enabled = !state.completing,
        onLanguageSelected = viewModel::selectLanguage,
        onStart = { viewModel.complete(WelcomeExit.SHOPPING) },
        onConnectHomeAssistant = { viewModel.complete(WelcomeExit.HOME_ASSISTANT) },
    )
}

@Composable
fun WelcomeScreen(
    language: AppLanguage,
    enabled: Boolean,
    onLanguageSelected: (AppLanguage) -> Unit,
    onStart: () -> Unit,
    onConnectHomeAssistant: () -> Unit,
) {
    // Surface (not a plain background) so texts get the theme's content colour in dark mode.
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
    Box(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Scrollable: the language choice must not push the buttons off small or zoomed screens.
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier =
                    Modifier
                        .size(128.dp)
                        .clip(RoundedCornerShape(36.dp))
                        .background(colorResource(R.color.launcher_background)),
            )
            Spacer(Modifier.height(32.dp))
            Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.welcome_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Text(stringResource(R.string.welcome_language), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            LanguageSelector(
                selected = language,
                onSelect = onLanguageSelected,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onStart, enabled = enabled, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.welcome_start), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onConnectHomeAssistant, enabled = enabled) {
                Text(stringResource(R.string.welcome_connect_ha))
            }
            Text(
                text = stringResource(R.string.welcome_ha_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
    }
}
