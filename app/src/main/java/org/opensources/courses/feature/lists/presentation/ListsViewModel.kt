package org.opensources.courses.feature.lists.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.opensources.courses.feature.lists.domain.ListNameRules
import org.opensources.courses.feature.lists.domain.ShoppingList
import org.opensources.courses.feature.lists.domain.ShoppingListRepository
import javax.inject.Inject

@HiltViewModel
class ListsViewModel
    @Inject
    constructor(
        private val repository: ShoppingListRepository,
    ) : ViewModel() {
        val lists: StateFlow<List<ShoppingList>> =
            repository.observeLists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

        private val mutableLastListWarning = MutableStateFlow(false)

        /** True after an attempt to delete the only remaining list. */
        val lastListWarning: StateFlow<Boolean> = mutableLastListWarning.asStateFlow()

        private val mutableCreatedListId = MutableStateFlow<String?>(null)

        /** The list just created, to open once; the screen then calls [createdListOpened]. */
        val createdListId: StateFlow<String?> = mutableCreatedListId.asStateFlow()

        fun create(name: String) {
            val cleanName = ListNameRules.sanitize(name) ?: return
            viewModelScope.launch { mutableCreatedListId.value = repository.createList(cleanName).id }
        }

        fun createdListOpened() {
            mutableCreatedListId.value = null
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

        fun lastListWarningShown() {
            mutableLastListWarning.value = false
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
