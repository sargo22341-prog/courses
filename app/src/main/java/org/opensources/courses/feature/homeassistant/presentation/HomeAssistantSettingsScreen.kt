package org.opensources.courses.feature.homeassistant.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.opensources.courses.R
import org.opensources.courses.core.designsystem.component.BackTopBar
import org.opensources.courses.core.designsystem.component.RadioRow
import org.opensources.courses.core.designsystem.component.SettingsCard
import org.opensources.courses.core.designsystem.component.StatusText
import org.opensources.courses.core.designsystem.component.SwitchRow
import org.opensources.courses.core.scanner.rememberQrCodeScanner
import org.opensources.courses.feature.homeassistant.domain.HaListMode

@Composable
fun HomeAssistantRoute(
    onBack: () -> Unit,
    viewModel: HomeAssistantSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scanQrCode = rememberQrCodeScanner(onScanned = viewModel::onTokenScanned, onUnavailable = viewModel::onScannerUnavailable)
    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.ha_title), onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(stringResource(R.string.ha_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HaConnectionCard(
                state = state,
                url = viewModel.urlInput,
                token = viewModel.tokenInput,
                onUrlChange = viewModel::onUrlChange,
                onTokenChange = viewModel::onTokenChange,
                onScanToken = scanQrCode,
                onEnabledChange = viewModel::setEnabled,
                onSave = viewModel::save,
                onTest = viewModel::testConnection,
            )
            if (state.config.enabled) {
                ListModeCard(state.config.listMode, viewModel::setListMode)
                HaLinkedListsCard(state, onChoose = viewModel::openPicker)
                SyncCard(state, onAutoSyncChange = viewModel::setAutoSync, onSyncNow = viewModel::syncNow)
            }
        }
    }
    state.pickerList?.let { list ->
        HaListPickerDialog(
            list = list,
            state = state,
            onLink = { entityId -> viewModel.linkToExisting(list.id, entityId) },
            onCreate = { viewModel.createInHomeAssistant(list.id) },
            onUnlink = { viewModel.unlink(list.id) },
            onDismiss = viewModel::closePicker,
        )
    }
}

@Composable
private fun ListModeCard(
    mode: HaListMode,
    onModeChange: (HaListMode) -> Unit,
) {
    SettingsCard(stringResource(R.string.ha_list_mode)) {
        Column(Modifier.selectableGroup()) {
            RadioRow(stringResource(R.string.ha_mode_all), mode == HaListMode.ALL_LISTS, onClick = { onModeChange(HaListMode.ALL_LISTS) })
            RadioRow(
                stringResource(R.string.ha_mode_app),
                mode == HaListMode.APP_CREATED_ONLY,
                onClick = { onModeChange(HaListMode.APP_CREATED_ONLY) },
            )
        }
    }
}

@Composable
private fun SyncCard(
    state: HaSettingsUiState,
    onAutoSyncChange: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
) {
    SettingsCard(stringResource(R.string.ha_sync_section)) {
        SwitchRow(stringResource(R.string.ha_auto_sync), state.config.autoSync, onAutoSyncChange)
        Button(onClick = onSyncNow, enabled = state.sync != HaActionStatus.Running) {
            Text(stringResource(if (state.sync == HaActionStatus.Running) R.string.sync_syncing else R.string.ha_sync_now))
        }
        (state.sync as? HaActionStatus.Done)?.let { StatusText(stringResource(it.message.text), it.message.isError) }
    }
}
