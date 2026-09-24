package org.opensources.courses.feature.lists.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.opensources.courses.core.sync.ImportableLists
import org.opensources.courses.core.sync.RemoteListChoice
import org.opensources.courses.core.sync.RemoteListImport
import org.opensources.courses.feature.lists.domain.ListNameRules
import org.opensources.courses.feature.lists.domain.ShoppingList
import org.opensources.courses.feature.lists.domain.ShoppingListRepository
import javax.inject.Inject

@HiltViewModel
class ListsViewModel
    @Inject
    constructor(
        private val repository: ShoppingListRepository,
        private val remoteListImport: RemoteListImport,
    ) : ViewModel() {
        val lists: StateFlow<List<ShoppingList>> =
            repository.observeLists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

        private val mutableLastListWarning = MutableStateFlow(false)

        /** True after an attempt to delete the only remaining list. */
        val lastListWarning: StateFlow<Boolean> = mutableLastListWarning.asStateFlow()

        private val mutableCreatedListId = MutableStateFlow<String?>(null)

        /** The list just created or imported, to open once; the screen then calls [createdListOpened]. */
        val createdListId: StateFlow<String?> = mutableCreatedListId.asStateFlow()

        /** The new list dialog offers to import a Home Assistant list instead. */
        val canImportRemoteList: StateFlow<Boolean> =
            remoteListImport.isAvailable.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

        private val mutableImportState = MutableStateFlow<ListImportUiState>(ListImportUiState.Closed)
        val importState: StateFlow<ListImportUiState> = mutableImportState.asStateFlow()
        private var importLoading: Job? = null

        fun create(name: String) {
            val cleanName = ListNameRules.sanitize(name) ?: return
            viewModelScope.launch { mutableCreatedListId.value = repository.createList(cleanName).id }
        }

        fun createdListOpened() {
            mutableCreatedListId.value = null
        }

        /** Reads the lists that can be imported; the only step of the lists screen that needs the network. */
        fun openImport() {
            importLoading?.cancel()
            mutableImportState.value = ListImportUiState.Loading
            importLoading =
                viewModelScope.launch {
                    mutableImportState.value =
                        when (val result = remoteListImport.importableLists()) {
                            is ImportableLists.Loaded -> ListImportUiState.Choosing(result.lists)
                            is ImportableLists.Failed -> ListImportUiState.Failed(result.reason)
                        }
                }
        }

        fun closeImport() {
            importLoading?.cancel()
            mutableImportState.value = ListImportUiState.Closed
        }

        /** The imported list opens at once, like a created one; its items arrive with the synchronisation. */
        fun importList(list: RemoteListChoice) {
            closeImport()
            viewModelScope.launch { mutableCreatedListId.value = remoteListImport.importList(list) }
        }

        fun rename(
            listId: String,
            name: String,
        ) {
            val cleanName = ListNameRules.sanitize(name) ?: return
            viewModelScope.launch { repository.renameList(listId, cleanName) }
        }

        fun delete(listId: String) {
            viewModelScope.launch {
                if (!repository.deleteList(listId)) mutableLastListWarning.value = true
            }
        }

        fun setDefault(listId: String) {
            viewModelScope.launch { repository.setDefaultList(listId) }
        }

        /** After a drag and drop: [orderedIds] is the whole new order. */
        fun reorder(orderedIds: List<String>) {
            if (orderedIds == lists.value.map { it.id }) return
            viewModelScope.launch { repository.reorderLists(orderedIds) }
        }

        /** One place up (negative [offset]) or down, from the menu: the same as a drag, without one. */
        fun move(
            listId: String,
            offset: Int,
        ) {
            val ids = lists.value.map { it.id }
            val from = ids.indexOf(listId)
            val to = from + offset
            if (from < 0 || to !in ids.indices) return
            reorder(ids.toMutableList().apply { add(to, removeAt(from)) })
        }

        fun lastListWarningShown() {
            mutableLastListWarning.value = false
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
