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
import kotlinx.coroutines.flow.SharedFlow
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
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import org.opensources.courses.feature.shopping.domain.ExactSuggestionFinder
import org.opensources.courses.feature.shopping.domain.GroupItemsByCategoryUseCase
import org.opensources.courses.feature.shopping.domain.ItemEntry
import org.opensources.courses.feature.shopping.domain.ItemEntryParser
import org.opensources.courses.feature.shopping.domain.ItemSection
import org.opensources.courses.feature.shopping.domain.ProductHistoryUseCase
import org.opensources.courses.feature.shopping.domain.QuantityStepper
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
        private val productHistory: ProductHistoryUseCase,
        private val preferences: AppPreferencesRepository,
        private val languages: AppLanguageRepository,
        private val syncCoordinator: SyncCoordinator,
        @ApplicationScope private val applicationScope: CoroutineScope,
    ) : ViewModel() {
        private val requestedListId: String? = savedStateHandle.toRoute<ShoppingDestination>().listId

        /** Held as Compose state so the text field never lags behind typing. */
        var query by mutableStateOf("")
            private set

        /**
         * Shared by the screen and the additions: adding an item reads the list shown instead of
         * querying Room again. Forgotten once nobody observes it, so it is never read stale.
         */
        private val currentList: SharedFlow<ShoppingList?> =
            if (requestedListId == null) {
                lists.observeDefaultList()
            } else {
                // A list deleted meanwhile falls back to the default list.
                lists.observeList(requestedListId).flatMapLatest { list -> if (list != null) flowOf(list) else lists.observeDefaultList() }
            }.distinctUntilChanged()
                .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0), replay = 1)

        private val groupByCategory: Flow<Boolean> = preferences.preferences.map { it.groupByCategory }.distinctUntilChanged()

        /**
         * Sections are computed from the same emission as the items, so both never disagree on screen.
         * Checked items are sorted too: checking one then changes no catalog query.
         */
        private val listContent: Flow<ListContent> =
            currentList.flatMapLatest { list ->
                if (list == null) return@flatMapLatest flowOf(ListContent.of(null, emptyList(), toBuySections = null))
                val listItems = items.observeItems(list.id)
                groupByCategory.flatMapLatest { grouped ->
                    if (grouped) {
                        groupItemsByCategory(listItems).map { ListContent.of(list, it.items, it.sections.withoutChecked()) }
                    } else {
                        listItems.map { ListContent.of(list, it, toBuySections = null) }
                    }
                }
            }

        /** Searched for the name only: "500 g de pâtes" suggests "Pâtes". */
        private val searchResults: Flow<SearchResults> =
            snapshotFlow { query }
                .debounce(SEARCH_DEBOUNCE_MILLIS)
                .mapLatest { text -> search(ItemEntryParser.parse(text)) }
                .onStart { emit(SearchResults(ItemEntry(""), emptyList(), exact = null)) }

        private val refreshing = MutableStateFlow(false)

        /** Hidden at once, deleted only once it can no longer be undone. */
        private val pendingDeletion = MutableStateFlow<ShoppingItem?>(null)

        private val justAdded = MutableStateFlow<String?>(null)

        /** "+" and "−" read the item again: quick taps must each count, not repeat a stale quantity. */
        private val quantityChanges = Mutex()

        /** Not even read while the history is turned off in the settings. */
        private val frequentProducts: Flow<List<ProductSuggestion>> =
            preferences.preferences
                .map { it.historyEnabled }
                .distinctUntilChanged()
                .flatMapLatest { enabled -> if (enabled) productHistory.observeFrequent() else flowOf(emptyList()) }

        /**
         * Computed only when the items, the pending deletion or the history change: the lists keep
         * their identity when the sync state or the suggestions change, so the list on screen is not
         * recomposed. An item being deleted is offered again in the history, as it left the list.
         */
        private val shownContent: Flow<ListContent> =
            combine(listContent, pendingDeletion, frequentProducts, justAdded) { content, deleted, frequent, added ->
                content.without(deleted).let { it.copy(history = productHistory.notInList(frequent, it.toBuy), highlightedItemId = added) }
            }

        val uiState: StateFlow<ShoppingUiState> =
            combine(
                shownContent,
                preferences.preferences,
                searchResults,
                syncCoordinator.snapshot,
                refreshing,
            ) { shown, prefs, found, sync, isRefreshing ->
                ShoppingUiState(
                    isLoading = shown.list == null,
                    listName = shown.list?.name.orEmpty(),
                    toBuy = shown.toBuy,
                    toBuySections = shown.toBuySections,
                    purchased = shown.purchased,
                    hidePurchased = prefs.hidePurchased,
                    suggestions = found.suggestions,
                    searchedEntry = found.entry,
                    exactSuggestion = found.exact,
                    history = shown.history,
                    sync = sync,
                    isRefreshing = isRefreshing,
                    pendingDeletion = shown.pendingDeletion,
                    highlightedItemId = shown.highlightedItemId,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ShoppingUiState())

        init {
            viewModelScope.launch { lists.ensureDefaultList(ShoppingListDefaults.defaultListName(languages.language.value)) }
        }

        fun onQueryChange(text: String) {
            query = text
        }

        /** A search suggestion or a product of the history, with the quantity typed if any. */
        fun onSuggestionSelected(suggestion: ProductSuggestion) {
            val entry = ItemEntryParser.parse(query)
            add(entry.copy(name = suggestion.name), suggestion.productId)
        }

        /**
         * Keyboard "done": the exact suggestion when there is one, otherwise the typed name. Suggestions
         * shown for older text (typed faster than the search) are searched again rather than trusted.
         */
        fun onSubmitQuery() {
            val entry = ItemEntryParser.parse(query)
            if (TextNormalizer.normalize(entry.name).isEmpty()) return
            val shown = uiState.value.takeIf { it.searchedEntry == entry }
            query = ""
            viewModelScope.launch {
                val exact = if (shown != null) shown.exactSuggestion else search(entry).exact
                addNow(if (exact == null) entry else entry.copy(name = exact.name), exact?.productId)
            }
        }

        fun onAddCustomItem() = add(ItemEntryParser.parse(query), catalogProductId = null)

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

        /** "+" or "−" on a row; "−" never goes below one step. */
        fun onChangeQuantity(
            item: ShoppingItem,
            increase: Boolean,
        ) {
            viewModelScope.launch {
                quantityChanges.withLock {
                    val current = items.getItems(item.listId).firstOrNull { it.id == item.id } ?: return@withLock
                    val quantity =
                        if (increase) {
                            QuantityStepper.increase(current.quantity, current.unit)
                        } else {
                            QuantityStepper.decrease(current.quantity, current.unit) ?: return@withLock
                        }
                    items.updateItem(current.id, current.name, quantity, current.unit)
                }
            }
        }

        /** The list scrolled to the item just added and lit it up. */
        fun onHighlightShown() {
            justAdded.value = null
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
            entry: ItemEntry,
            catalogProductId: String?,
        ) {
            query = ""
            viewModelScope.launch { addNow(entry, catalogProductId) }
        }

        private suspend fun addNow(
            entry: ItemEntry,
            catalogProductId: String?,
        ) {
            val listId = currentListId() ?: return
            addItem(listId, entry.name, catalogProductId, entry.quantity, entry.unit)?.let { justAdded.value = it.id }
        }

        private suspend fun search(entry: ItemEntry): SearchResults {
            if (entry.name.isBlank()) return SearchResults(entry, emptyList(), exact = null)
            val found = searchSuggestions(entry.name)
            return SearchResults(entry, found, ExactSuggestionFinder.find(found, entry.name, languages.language.value))
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

        /** [pendingDeletion] is already left out of the items and the sections. */
        private data class ListContent(
            val list: ShoppingList?,
            val toBuy: List<ShoppingItem>,
            val purchased: List<ShoppingItem>,
            val toBuySections: List<ItemSection>?,
            val pendingDeletion: ShoppingItem? = null,
            val history: List<ProductSuggestion> = emptyList(),
            val highlightedItemId: String? = null,
        ) {
            fun without(deleted: ShoppingItem?): ListContent =
                if (deleted == null) {
                    this
                } else {
                    copy(
                        toBuy = toBuy.filterNot { it.id == deleted.id },
                        purchased = purchased.filterNot { it.id == deleted.id },
                        toBuySections =
                            toBuySections
                                ?.map { section -> section.copy(items = section.items.filterNot { it.id == deleted.id }) }
                                ?.filter { it.items.isNotEmpty() },
                        pendingDeletion = deleted,
                    )
                }

            companion object {
                /** Items to buy and items bought, split once for every later emission that keeps them. */
                fun of(
                    list: ShoppingList?,
                    items: List<ShoppingItem>,
                    toBuySections: List<ItemSection>?,
                ): ListContent {
                    val (purchased, toBuy) = items.partition { it.isChecked }
                    return ListContent(list, toBuy, purchased, toBuySections)
                }
            }
        }

        private data class SearchResults(
            val entry: ItemEntry,
            val suggestions: List<ProductSuggestion>,
            val exact: ProductSuggestion?,
        )

        private companion object {
            const val SEARCH_DEBOUNCE_MILLIS = 60L
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
