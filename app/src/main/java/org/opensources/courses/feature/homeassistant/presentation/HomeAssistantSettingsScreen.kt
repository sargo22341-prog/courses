package org.opensources.courses.feature.homeassistant.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.opensources.courses.R
import org.opensources.courses.core.designsystem.component.BackTopBar
import org.opensources.courses.core.designsystem.component.SettingsCard
import org.opensources.courses.core.designsystem.component.StatusText
import org.opensources.courses.core.designsystem.component.SwitchRow
import org.opensources.courses.core.scanner.QrCodeScannerDialog

@Composable
fun HomeAssistantRoute(
    onBack: () -> Unit,
    viewModel: HomeAssistantSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var scanningToken by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.ha_title), onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(stringResource(R.string.ha_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LocalNetworkPermissionCard()
            // The card folds itself when a connection is saved: it waits for the saved configuration.
            if (state.isLoaded) {
                HaConnectionCard(
                    state = state,
                    url = viewModel.urlInput,
                    token = viewModel.tokenInput,
                    onUrlChange = viewModel::onUrlChange,
                    onTokenChange = viewModel::onTokenChange,
                    onScanToken = { scanningToken = true },
                    onEnabledChange = viewModel::setEnabled,
                    onSave = viewModel::save,
                    onTest = viewModel::testConnection,
                )
            }
            if (state.config.enabled) {
                HaListModeCard(state.config.listMode, state.importedListCount, viewModel::setListMode)
                HaLinkedListsCard(state, onAutoCreateChange = viewModel::setAutoCreateLists, onChoose = viewModel::openPicker)
                SyncCard(state, onAutoSyncChange = viewModel::setAutoSync, onSyncNow = viewModel::syncNow)
            }
        }
    }
    if (scanningToken) {
        QrCodeScannerDialog(
            onScanned = { scanned ->
                scanningToken = false
                viewModel.onTokenScanned(scanned)
            },
            onCameraUnavailable = {
                scanningToken = false
                viewModel.onScannerUnavailable()
            },
            onDismiss = { scanningToken = false },
        )
    }
    state.pickerList?.let { list ->
        HaListPickerDialog(
            list = list,
            state = state,
            onLink = { entityId -> viewModel.linkToExisting(list.id, entityId) },
            onCreate = { viewModel.createInHomeAssistant(list.id) },
            onUnlink = { viewModel.unlink(list.id) },
            onDismiss = { viewModel.closePicker(list.id) },
        )
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
