package org.opensources.courses.feature.settings.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.opensources.courses.R
import org.opensources.courses.core.designsystem.component.BackTopBar
import org.opensources.courses.core.designsystem.component.SettingsCard
import org.opensources.courses.core.designsystem.component.SwitchRow
import org.opensources.courses.feature.catalog.presentation.CatalogSection
import org.opensources.courses.feature.catalog.presentation.CatalogSettingsViewModel
import org.opensources.courses.feature.settings.presentation.components.ThemeModeSelector

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenHomeAssistant: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    catalogViewModel: CatalogSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val catalogState by catalogViewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.settings_title), onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SettingsCard(stringResource(R.string.settings_theme)) { ThemeModeSelector(state.themeMode, viewModel::setThemeMode) }
            ShoppingListSection(state.groupByCategory, viewModel::setGroupByCategory)
            HomeAssistantEntry(state, onOpenHomeAssistant)
            CatalogSection(catalogState, onSyncNow = catalogViewModel::forceSync)
        }
    }
}

@Composable
private fun ShoppingListSection(
    groupByCategory: Boolean,
    onGroupByCategoryChange: (Boolean) -> Unit,
) {
    SettingsCard(stringResource(R.string.settings_shopping_list)) {
        SwitchRow(stringResource(R.string.settings_group_by_category), groupByCategory, onGroupByCategoryChange)
        Text(
            text = stringResource(R.string.settings_group_by_category_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HomeAssistantEntry(
    state: SettingsUiState,
    onOpen: () -> Unit,
) {
    SettingsCard(stringResource(R.string.settings_home_assistant)) {
        Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text =
                    if (state.homeAssistantEnabled) {
                        stringResource(R.string.settings_home_assistant_on, state.homeAssistantUrl)
                    } else {
                        stringResource(R.string.settings_home_assistant_off)
                    },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
