package org.opensources.courses.feature.shopping.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.opensources.courses.core.sync.SyncCoordinator
import org.opensources.courses.feature.catalog.domain.ProductSuggestion
import org.opensources.courses.feature.catalog.domain.SearchSuggestionsUseCase
import org.opensources.courses.feature.catalog.domain.TextNormalizer
import org.opensources.courses.feature.lists.domain.ShoppingList
import org.opensources.courses.feature.lists.domain.ShoppingListDefaults
import org.opensources.courses.feature.lists.domain.ShoppingListRepository
import org.opensources.courses.feature.settings.domain.AppPreferencesRepository
import org.opensources.courses.feature.shopping.domain.AddItemUseCase
import org.opensources.courses.feature.shopping.domain.ShoppingItem
import org.opensources.courses.feature.shopping.domain.ShoppingItemRepository
import org.opensources.courses.navigation.ShoppingDestination
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ShoppingViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val lists: ShoppingListRepository,
        private val items: ShoppingItemRepository,
        private val addItem: AddItemUseCase,
        private val searchSuggestions: SearchSuggestionsUseCase,
        private val preferences: AppPreferencesRepository,
        syncCoordinator: SyncCoordinator,
    ) : ViewModel() {
        private val requestedListId: String? = savedStateHandle.toRoute<ShoppingDestination>().listId

        /** Held as Compose state so the text field never lags behind typing. */
        var query by mutableStateOf("")
            private set

        private val currentList: Flow<ShoppingList?> =
            if (requestedListId == null) {
                lists.observeDefaultList()
            } else {
                // A list deleted meanwhile falls back to the default list.
                lists.observeList(requestedListId).flatMapLatest { list -> if (list != null) flowOf(list) else lists.observeDefaultList() }
            }

        private val listWithItems: Flow<Pair<ShoppingList?, List<ShoppingItem>>> =
            currentList.flatMapLatest { list ->
                if (list == null) flowOf(null to emptyList()) else items.observeItems(list.id).map { list to it }
            }

        private val suggestions: Flow<List<ProductSuggestion>> =
            snapshotFlow { query }
                .debounce(SEARCH_DEBOUNCE_MILLIS)
                .mapLatest { text -> if (text.isBlank()) emptyList() else searchSuggestions(text) }
                .onStart { emit(emptyList()) }

        val uiState: StateFlow<ShoppingUiState> =
            combine(listWithItems, preferences.preferences, suggestions, syncCoordinator.snapshot) { (list, all), prefs, found, sync ->
                ShoppingUiState(
                    isLoading = list == null,
                    listName = list?.name.orEmpty(),
                    toBuy = all.filterNot { it.isChecked },
                    purchased = all.filter { it.isChecked },
                    hidePurchased = prefs.hidePurchased,
                    suggestions = found,
                    sync = sync,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ShoppingUiState())

        init {
            viewModelScope.launch { lists.ensureDefaultList(ShoppingListDefaults.DEFAULT_LIST_NAME) }
        }

        fun onQueryChange(text: String) {
            query = text
        }

        fun onSuggestionSelected(suggestion: ProductSuggestion) = add(suggestion.name, suggestion.productId)

        /** Keyboard "done": the exact suggestion when there is one, otherwise the typed text. */
        fun onSubmitQuery() {
            val normalized = TextNormalizer.normalize(query)
            if (normalized.isEmpty()) return
            val exact = uiState.value.suggestions.firstOrNull { TextNormalizer.normalize(it.name) == normalized }
            add(exact?.name ?: query, exact?.productId)
        }

        fun onAddCustomItem() = add(query, catalogProductId = null)

        fun onToggleItem(item: ShoppingItem) {
            viewModelScope.launch { items.setChecked(item.id, !item.isChecked) }
        }

        fun onDeleteItem(item: ShoppingItem) {
            viewModelScope.launch { items.deleteItem(item.id) }
        }

        fun onSaveItem(
            item: ShoppingItem,
            name: String,
            quantity: Double,
            unit: String?,
        ) {
            val cleanName = name.trim()
            if (cleanName.isEmpty()) return
            viewModelScope.launch { items.updateItem(item.id, cleanName, quantity, unit?.trim()?.takeIf { it.isNotEmpty() }) }
        }

        fun onToggleHidePurchased() {
            viewModelScope.launch { preferences.setHidePurchased(!uiState.value.hidePurchased) }
        }

        fun onDeletePurchased() {
            viewModelScope.launch { currentListId()?.let { items.deletePurchased(it) } }
        }

        private fun add(
            name: String,
            catalogProductId: String?,
        ) {
            query = ""
            viewModelScope.launch {
                val listId = currentListId() ?: return@launch
                addItem(listId, name, catalogProductId)
            }
        }

        private suspend fun currentListId(): String? = currentList.first()?.id

        private companion object {
            const val SEARCH_DEBOUNCE_MILLIS = 60L
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
