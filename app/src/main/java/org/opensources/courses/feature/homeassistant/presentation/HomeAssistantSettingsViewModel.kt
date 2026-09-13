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
        listRepository: ShoppingListRepository,
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

        val uiState: StateFlow<HaSettingsUiState> =
            combine(
                configRepository.config,
                listRepository.observeLists(),
                linkRepository.observeTrackedEntityIds(),
                combine(connection, sync, ::Pair),
                combine(remoteLists, pickerListId, ::Pair),
            ) { config, lists, tracked, (connectionStatus, syncStatus), (remote, picker) ->
                HaSettingsUiState(config, lists, tracked, connectionStatus, syncStatus, remote, picker)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HaSettingsUiState())

        init {
            viewModelScope.launch {
                val config = configRepository.config.first()
                if (urlInput.isEmpty()) urlInput = config.baseUrl
                if (config.enabled && config.isConfigured) loadRemoteLists()
            }
        }

        fun onUrlChange(value: String) {
            urlInput = value
        }

        fun onTokenChange(value: String) {
            tokenInput = value
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

        fun setListMode(mode: HaListMode) {
            viewModelScope.launch { configRepository.setListMode(mode) }
        }

        fun setAutoSync(enabled: Boolean) {
            viewModelScope.launch { configRepository.setAutoSync(enabled) }
        }

        fun openPicker(listId: String) {
            pickerListId.value = listId
            if (remoteLists.value !is RemoteListsState.Loaded) viewModelScope.launch { loadRemoteLists() }
        }

        fun closePicker() {
            pickerListId.value = null
        }

        fun linkToExisting(
            listId: String,
            entityId: String,
        ) = changeLink { linkRepository.linkToExisting(listId, entityId) }

        fun createInHomeAssistant(listId: String) = changeLink { linkRepository.createInHomeAssistant(listId) }

        fun unlink(listId: String) = changeLink { linkRepository.unlink(listId) }

        fun syncNow() {
            viewModelScope.launch {
                sync.value = HaActionStatus.Running
                sync.value = HaActionStatus.Done(HaMessage.from(syncCoordinator.syncNow()))
            }
        }

        private fun changeLink(block: suspend () -> Unit) {
            pickerListId.value = null
            viewModelScope.launch {
                block()
                syncCoordinator.requestSync()
            }
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
