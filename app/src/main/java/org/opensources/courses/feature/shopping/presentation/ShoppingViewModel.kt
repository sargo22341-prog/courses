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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.opensources.courses.core.common.ApplicationScope
import org.opensources.courses.core.sync.SyncCoordinator
import org.opensources.courses.feature.catalog.domain.ProductSuggestion
import org.opensources.courses.feature.catalog.domain.SearchSuggestionsUseCase
import org.opensources.courses.feature.catalog.domain.TextNormalizer
import org.opensources.courses.feature.language.domain.AppLanguageRepository
import org.opensources.courses.feature.lists.domain.ShoppingList
import org.opensources.courses.feature.lists.domain.ShoppingListDefaults
import org.opensources.courses.feature.lists.domain.ShoppingListRepository
import org.opensources.courses.feature.settings.domain.AppPreferencesRepository
import org.opensources.courses.feature.shopping.domain.AddItemUseCase
import org.opensources.courses.feature.shopping.domain.GroupItemsByCategoryUseCase
import org.opensources.courses.feature.shopping.domain.ItemSection
import org.opensources.courses.feature.shopping.domain.ShoppingItem
import org.opensources.courses.feature.shopping.domain.ShoppingItemRepository
import org.opensources.courses.feature.shopping.domain.withoutChecked
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
        private val groupItemsByCategory: GroupItemsByCategoryUseCase,
        private val preferences: AppPreferencesRepository,
        private val languages: AppLanguageRepository,
        private val syncCoordinator: SyncCoordinator,
        @ApplicationScope private val applicationScope: CoroutineScope,
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
            }.distinctUntilChanged()

        private val groupByCategory: Flow<Boolean> = preferences.preferences.map { it.groupByCategory }.distinctUntilChanged()

        /**
         * Sections are computed from the same emission as the items, so both never disagree on screen.
         * Checked items are sorted too: checking one then changes no catalog query.
         */
        private val listContent: Flow<ListContent> =
            currentList.flatMapLatest { list ->
                if (list == null) return@flatMapLatest flowOf(ListContent(null, emptyList(), toBuySections = null))
                val listItems = items.observeItems(list.id)
                groupByCategory.flatMapLatest { grouped ->
                    if (grouped) {
                        groupItemsByCategory(listItems).map { ListContent(list, it.items, it.sections.withoutChecked()) }
                    } else {
                        listItems.map { ListContent(list, it, toBuySections = null) }
                    }
                }
            }

        private val suggestions: Flow<List<ProductSuggestion>> =
            snapshotFlow { query }
                .debounce(SEARCH_DEBOUNCE_MILLIS)
                .mapLatest { text -> if (text.isBlank()) emptyList() else searchSuggestions(text) }
                .onStart { emit(emptyList()) }

        private val refreshing = MutableStateFlow(false)

        /** Hidden at once, deleted only once it can no longer be undone. */
        private val pendingDeletion = MutableStateFlow<ShoppingItem?>(null)

        val uiState: StateFlow<ShoppingUiState> =
            combine(
                listContent,
                preferences.preferences,
                suggestions,
                syncCoordinator.snapshot,
                combine(refreshing, pendingDeletion, ::Pair),
            ) { content, prefs, found, sync, (isRefreshing, deleted) ->
                val shown = content.without(deleted)
                ShoppingUiState(
                    isLoading = shown.list == null,
                    listName = shown.list?.name.orEmpty(),
                    toBuy = shown.items.filterNot { it.isChecked },
                    toBuySections = shown.toBuySections,
                    purchased = shown.items.filter { it.isChecked },
                    hidePurchased = prefs.hidePurchased,
                    suggestions = found,
                    sync = sync,
                    isRefreshing = isRefreshing,
                    pendingDeletion = deleted,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ShoppingUiState())

        init {
            viewModelScope.launch { lists.ensureDefaultList(ShoppingListDefaults.defaultListName(languages.language.value)) }
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

        /**
         * The item disappears at once and can be brought back ([onUndoDeletion]) until
         * [onDeletionConfirmed]; deleting another one meanwhile confirms the previous deletion.
         */
        fun onDeleteItem(item: ShoppingItem) {
            val previous = pendingDeletion.getAndUpdate { item }
            if (previous != null && previous.id != item.id) delete(previous)
        }

        fun onUndoDeletion() {
            pendingDeletion.value = null
        }

        /** The undo offer for [item] ended without being used. */
        fun onDeletionConfirmed(item: ShoppingItem) {
            if (pendingDeletion.compareAndSet(item, null)) delete(item)
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

        /** Pull to refresh: an explicit synchronisation; its result shows in the sync indicator. */
        fun onRefresh() {
            if (refreshing.value) return
            viewModelScope.launch {
                refreshing.value = true
                try {
                    syncCoordinator.syncNow()
                } finally {
                    refreshing.value = false
                }
            }
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

        /** Leaving the screen for good ends the undo offer: the deletion is done. */
        override fun onCleared() {
            pendingDeletion.value?.let(::delete)
        }

        // Outlives the screen, which may be closing (onCleared).
        private fun delete(item: ShoppingItem) {
            applicationScope.launch { items.deleteItem(item.id) }
        }

        private suspend fun currentListId(): String? = currentList.first()?.id

        private data class ListContent(
            val list: ShoppingList?,
            val items: List<ShoppingItem>,
            val toBuySections: List<ItemSection>?,
        ) {
            fun without(deleted: ShoppingItem?): ListContent =
                if (deleted == null) {
                    this
                } else {
                    copy(
                        items = items.filterNot { it.id == deleted.id },
                        toBuySections =
                            toBuySections
                                ?.map { section -> section.copy(items = section.items.filterNot { it.id == deleted.id }) }
                                ?.filter { it.items.isNotEmpty() },
                    )
                }
        }

        private companion object {
            const val SEARCH_DEBOUNCE_MILLIS = 60L
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
