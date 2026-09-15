package org.opensources.courses.feature.homeassistant.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.opensources.courses.core.sync.SyncCoordinator
import org.opensources.courses.feature.homeassistant.domain.HaConfigRepository
import org.opensources.courses.feature.homeassistant.domain.HaListLinkRepository
import org.opensources.courses.feature.homeassistant.domain.HaListMode
import org.opensources.courses.feature.homeassistant.domain.HaTokenParser
import org.opensources.courses.feature.homeassistant.domain.HaUrlNormalizer
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantException
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantGateway
import org.opensources.courses.feature.lists.domain.ShoppingListRepository
import javax.inject.Inject

@HiltViewModel
class HomeAssistantSettingsViewModel
    @Inject
    constructor(
        private val configRepository: HaConfigRepository,
        private val gateway: HomeAssistantGateway,
        private val linkRepository: HaListLinkRepository,
        private val listRepository: ShoppingListRepository,
        private val syncCoordinator: SyncCoordinator,
    ) : ViewModel() {
        var urlInput by mutableStateOf("")
            private set
        var tokenInput by mutableStateOf("")
            private set

        private val connection = MutableStateFlow<HaActionStatus>(HaActionStatus.Idle)
        private val sync = MutableStateFlow<HaActionStatus>(HaActionStatus.Idle)
        private val remoteLists = MutableStateFlow<RemoteListsState>(RemoteListsState.NotLoaded)
        private val pickerListId = MutableStateFlow<String?>(null)
        private val setupListIds = MutableStateFlow<List<String>>(emptyList())

        val uiState: StateFlow<HaSettingsUiState> =
            combine(
                configRepository.config,
                listRepository.observeLists(),
                combine(connection, sync, ::Pair),
                combine(remoteLists, pickerListId, setupListIds, ::Triple),
            ) { config, lists, (connectionStatus, syncStatus), (remote, picker, setup) ->
                HaSettingsUiState(config, lists, connectionStatus, syncStatus, remote, picker, setup, isLoaded = true)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HaSettingsUiState())

        init {
            viewModelScope.launch {
                val config = configRepository.config.first()
                if (urlInput.isEmpty()) urlInput = config.baseUrl
                if (config.enabled && config.isConfigured) loadRemoteLists()
            }
            viewModelScope.launch { startListsSetup() }
        }

        fun onUrlChange(value: String) {
            urlInput = value
        }

        fun onTokenChange(value: String) {
            tokenInput = value
        }

        /** Content of a scanned QR code: accepted only if it is a Home Assistant token. */
        fun onTokenScanned(scanned: String) {
            val token = HaTokenParser.parse(scanned)
            if (token == null) {
                connection.value = HaActionStatus.Done(HaMessage.INVALID_TOKEN_QR)
                return
            }
            tokenInput = token
            connection.value = HaActionStatus.Done(HaMessage.TOKEN_SCANNED)
        }

        fun onScannerUnavailable() {
            connection.value = HaActionStatus.Done(HaMessage.SCANNER_UNAVAILABLE)
        }

        fun save() {
            val url = HaUrlNormalizer.normalize(urlInput)
            if (url == null) {
                connection.value = HaActionStatus.Done(HaMessage.INVALID_URL)
                return
            }
            viewModelScope.launch {
                configRepository.saveConnection(url, tokenInput)
                urlInput = url
                tokenInput = ""
                remoteLists.value = RemoteListsState.NotLoaded
                connection.value = HaActionStatus.Done(HaMessage.SAVED)
            }
        }

        fun testConnection() {
            viewModelScope.launch {
                connection.value = HaActionStatus.Running
                val credentials = configRepository.credentialsFor(urlInput, tokenInput)
                connection.value =
                    when {
                        HaUrlNormalizer.normalize(urlInput) == null -> HaActionStatus.Done(HaMessage.INVALID_URL)
                        credentials == null -> HaActionStatus.Done(HaMessage.MISSING_TOKEN)
                        else ->
                            try {
                                gateway.testConnection(credentials)
                                HaActionStatus.Done(HaMessage.TEST_OK)
                            } catch (exception: HomeAssistantException) {
                                HaActionStatus.Done(HaMessage.from(exception.kind))
                            }
                    }
            }
        }

        fun setEnabled(enabled: Boolean) {
            viewModelScope.launch {
                if (enabled && !configRepository.config.first().isConfigured) {
                    connection.value = HaActionStatus.Done(HaMessage.ENABLE_REQUIRES_CONFIG)
                    return@launch
                }
                configRepository.setEnabled(enabled)
                if (enabled) {
                    loadRemoteLists()
                    syncCoordinator.requestSync()
                }
            }
        }

        /**
         * "All lists" imports the Home Assistant lists straight away. Leaving it removes the imported
         * lists (the screen asks first); the engine removes them again at its next synchronisation, in
         * case one running meanwhile imported a list.
         */
        fun setListMode(mode: HaListMode) {
            viewModelScope.launch {
                configRepository.setListMode(mode)
                when (mode) {
                    HaListMode.ALL_LISTS -> syncNow()
                    HaListMode.APP_CREATED_ONLY -> linkRepository.removeImportedLists()
                }
            }
        }

        fun setAutoSync(enabled: Boolean) {
            viewModelScope.launch { configRepository.setAutoSync(enabled) }
        }

        fun openPicker(listId: String) {
            pickerListId.value = listId
            if (remoteLists.value !is RemoteListsState.Loaded) viewModelScope.launch { loadRemoteLists() }
        }

        fun setAutoCreateLists(enabled: Boolean) {
            viewModelScope.launch { configRepository.setAutoCreateLists(enabled) }
        }

        /** Closing the picker during the first setup leaves the list on this phone only. */
        fun closePicker(listId: String) {
            pickerListId.value = null
            completeSetupStep(listId)
        }

        fun linkToExisting(
            listId: String,
            entityId: String,
        ) = changeLink(listId) { linkRepository.linkToExisting(listId, entityId) }

        fun createInHomeAssistant(listId: String) = changeLink(listId) { linkRepository.createInHomeAssistant(listId) }

        fun unlink(listId: String) = changeLink(listId) { linkRepository.unlink(listId) }

        fun syncNow() {
            viewModelScope.launch {
                sync.value = HaActionStatus.Running
                sync.value = HaActionStatus.Done(HaMessage.from(syncCoordinator.syncNow()))
            }
        }

        private fun changeLink(
            listId: String,
            block: suspend () -> Unit,
        ) {
            pickerListId.value = null
            completeSetupStep(listId)
            viewModelScope.launch {
                block()
                syncCoordinator.requestSync()
            }
        }

        /**
         * First setup: once Home Assistant is enabled and configured, each list that existed before
         * is offered in turn (create it in Home Assistant, link it to an existing list, or keep it
         * local). Asked only once; later lists follow the automatic creation setting.
         */
        private suspend fun startListsSetup() {
            configRepository.config.first { it.enabled && it.isConfigured && !it.listsSetupDone }
            val unsynchronized = listRepository.observeLists().first().filterNot { it.isSynchronized }.map { it.id }
            if (unsynchronized.isEmpty()) {
                configRepository.setListsSetupDone()
                return
            }
            setupListIds.value = unsynchronized
            if (remoteLists.value !is RemoteListsState.Loaded) loadRemoteLists()
        }

        private fun completeSetupStep(listId: String) {
            val remaining = setupListIds.value - listId
            if (remaining.size == setupListIds.value.size) return
            setupListIds.value = remaining
            if (remaining.isEmpty()) viewModelScope.launch { configRepository.setListsSetupDone() }
        }

        private suspend fun loadRemoteLists() {
            val config = configRepository.config.first()
            val credentials = configRepository.credentialsFor(config.baseUrl, typedToken = "")
            if (credentials == null) {
                remoteLists.value = RemoteListsState.Failed(HaMessage.MISSING_TOKEN)
                return
            }
            remoteLists.value = RemoteListsState.Loading
            remoteLists.value =
                try {
                    RemoteListsState.Loaded(gateway.getTodoLists(credentials))
                } catch (exception: HomeAssistantException) {
                    RemoteListsState.Failed(HaMessage.from(exception.kind))
                }
        }
    }
