package org.opensources.courses.feature.shopping.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.opensources.courses.R
import org.opensources.courses.feature.catalog.domain.ProductSuggestion
import org.opensources.courses.feature.shopping.domain.ShoppingItem
import org.opensources.courses.feature.shopping.presentation.components.AddItemField
import org.opensources.courses.feature.shopping.presentation.components.DeletePurchasedDialog
import org.opensources.courses.feature.shopping.presentation.components.EditItemDialog
import org.opensources.courses.feature.shopping.presentation.components.HistoryPanel
import org.opensources.courses.feature.shopping.presentation.components.ItemRowActions
import org.opensources.courses.feature.shopping.presentation.components.PurchasedFooter
import org.opensources.courses.feature.shopping.presentation.components.ShoppingListContent
import org.opensources.courses.feature.shopping.presentation.components.ShoppingTopBar
import org.opensources.courses.feature.shopping.presentation.components.SuggestionsPanel

@Composable
fun ShoppingRoute(
    onOpenLists: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ShoppingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnOpenLists by rememberUpdatedState(onOpenLists)
    val currentOnOpenSettings by rememberUpdatedState(onOpenSettings)
    // Created once: new callbacks at each keystroke would recompose the whole screen, bars included.
    val actions =
        remember(viewModel) {
            ShoppingActions(
                onQueryChange = viewModel::onQueryChange,
                onSubmitQuery = viewModel::onSubmitQuery,
                onSuggestionSelected = viewModel::onSuggestionSelected,
                onAddCustomItem = viewModel::onAddCustomItem,
                onToggleItem = viewModel::onToggleItem,
                onDeleteItem = viewModel::onDeleteItem,
                onUndoDeletion = viewModel::onUndoDeletion,
                onDeletionConfirmed = viewModel::onDeletionConfirmed,
                onSaveItem = viewModel::onSaveItem,
                onChangeQuantity = viewModel::onChangeQuantity,
                onHighlightShown = viewModel::onHighlightShown,
                onToggleHidePurchased = viewModel::onToggleHidePurchased,
                onDeletePurchased = viewModel::onDeletePurchased,
                onRefresh = viewModel::onRefresh,
                onOpenLists = { currentOnOpenLists() },
                onOpenSettings = { currentOnOpenSettings() },
            )
        }
    ShoppingScreen(state = state, query = viewModel.query, actions = actions)
}

/**
 * [onDeletePurchased] deletes: it is only called once the user confirmed. [onDeleteItem] only hides
 * the item, until [onUndoDeletion] or [onDeletionConfirmed].
 */
@Immutable
class ShoppingActions(
    val onQueryChange: (String) -> Unit = {},
    val onSubmitQuery: () -> Unit = {},
    val onSuggestionSelected: (ProductSuggestion) -> Unit = {},
    val onAddCustomItem: () -> Unit = {},
    val onToggleItem: (ShoppingItem) -> Unit = {},
    val onDeleteItem: (ShoppingItem) -> Unit = {},
    val onUndoDeletion: () -> Unit = {},
    val onDeletionConfirmed: (ShoppingItem) -> Unit = {},
    val onSaveItem: (ShoppingItem, String, Double, String?) -> Unit = { _, _, _, _ -> },
    val onChangeQuantity: (ShoppingItem, Boolean) -> Unit = { _, _ -> },
    val onHighlightShown: () -> Unit = {},
    val onToggleHidePurchased: () -> Unit = {},
    val onDeletePurchased: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onOpenLists: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
)

@Composable
fun ShoppingScreen(
    state: ShoppingUiState,
    query: String,
    actions: ShoppingActions,
) {
    var editedItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDeletePurchased by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val searching = query.isNotBlank()
    var fieldFocused by remember { mutableStateOf(false) }
    val showsHistory = fieldFocused && !searching && state.history.isNotEmpty()
    val focusManager = LocalFocusManager.current
    // Kept while the suggestions are shown: the list comes back where it was left.
    val listState = rememberLazyListState()
    val rowActions =
        remember(actions) { ItemRowActions(actions.onToggleItem, actions.onDeleteItem, { editedItemId = it.id }, actions.onChangeQuantity) }
    // The history belongs to typing: closing the keyboard leaves it, as does "back" without an
    // on-screen keyboard (hardware keyboard).
    ClearFocusWhenKeyboardCloses(focusManager)
    BackHandler(enabled = showsHistory) { focusManager.clearFocus() }
    state.pendingDeletion?.let { DeletionUndoOffer(it, snackbarHostState, actions) }
    Scaffold(
        topBar = { ShoppingTopBar(state.listName, state.sync, actions.onOpenLists, actions.onOpenSettings) },
        bottomBar = {
            AnimatedVisibility(visible = state.purchased.isNotEmpty() && !searching && !showsHistory) {
                PurchasedFooter(
                    purchasedCount = state.purchased.size,
                    hidePurchased = state.hidePurchased,
                    onToggleHidePurchased = actions.onToggleHidePurchased,
                    onRequestDeletePurchased = { confirmDeletePurchased = true },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            AddItemField(
                query = query,
                onQueryChange = actions.onQueryChange,
                onSubmit = actions.onSubmitQuery,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                onFocusChange = { fieldFocused = it },
            )
            PanelContent(panelFor(showsHistory, searching), state, actions, rowActions, listState, onRequestDeletePurchased = { confirmDeletePurchased = true })
        }
    }
    EditItemDialogHost(state, editedItemId, actions, onClose = { editedItemId = null })
    if (confirmDeletePurchased) {
        DeletePurchasedDialog(
            onConfirm = {
                confirmDeletePurchased = false
                actions.onDeletePurchased()
            },
            onDismiss = { confirmDeletePurchased = false },
        )
    }
}

/** What the space under the field shows. */
private enum class Panel { HISTORY, SUGGESTIONS, LIST }

private const val PANEL_FADE_MILLIS = 150

private fun panelFor(
    showsHistory: Boolean,
    searching: Boolean,
): Panel =
    when {
        showsHistory -> Panel.HISTORY
        searching -> Panel.SUGGESTIONS
        else -> Panel.LIST
    }

/** The [panel] under the field; switching fades one into the other. */
@Composable
private fun PanelContent(
    panel: Panel,
    state: ShoppingUiState,
    actions: ShoppingActions,
    rowActions: ItemRowActions,
    listState: LazyListState,
    onRequestDeletePurchased: () -> Unit,
) {
    AnimatedContent(
        targetState = panel,
        transitionSpec = { fadeIn(tween(PANEL_FADE_MILLIS)) togetherWith fadeOut(tween(PANEL_FADE_MILLIS)) },
        label = "panel",
    ) { shown ->
        when (shown) {
            // The field keeps the focus: several products can be picked in a row.
            Panel.HISTORY -> HistoryPanel(history = state.history, onProductSelected = actions.onSuggestionSelected)
            Panel.SUGGESTIONS ->
                SuggestionsPanel(
                    entry = state.searchedEntry,
                    suggestions = state.suggestions,
                    offersCustomItem = state.offersCustomItem,
                    onSuggestionSelected = actions.onSuggestionSelected,
                    onAddCustomItem = actions.onAddCustomItem,
                )
            Panel.LIST ->
                RefreshableContent(enabled = state.sync.remoteEnabled, isRefreshing = state.isRefreshing, onRefresh = actions.onRefresh) {
                    ShoppingListContent(
                        isLoading = state.isLoading,
                        toBuy = state.toBuy,
                        toBuySections = state.toBuySections,
                        purchased = state.purchased,
                        hidePurchased = state.hidePurchased,
                        actions = rowActions,
                        onRequestDeletePurchased = onRequestDeletePurchased,
                        highlightedItemId = state.highlightedItemId,
                        onHighlightShown = actions.onHighlightShown,
                        listState = listState,
                    )
                }
        }
    }
}

/**
 * "« Lait » supprimé · Annuler". Leaving the composition (undone, or replaced by another deletion)
 * dismisses the snackbar without confirming anything.
 */
@Composable
private fun DeletionUndoOffer(
    item: ShoppingItem,
    snackbarHostState: SnackbarHostState,
    actions: ShoppingActions,
) {
    val message = stringResource(R.string.shopping_item_deleted, item.name)
    val undo = stringResource(R.string.action_undo)
    val currentActions by rememberUpdatedState(actions)
    LaunchedEffect(item.id) {
        when (snackbarHostState.showSnackbar(message, actionLabel = undo, duration = SnackbarDuration.Short)) {
            SnackbarResult.ActionPerformed -> currentActions.onUndoDeletion()
            SnackbarResult.Dismissed -> currentActions.onDeletionConfirmed(item)
        }
    }
}

/** Only on a shown → hidden change: the field is focused before the keyboard opens. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClearFocusWhenKeyboardCloses(focusManager: FocusManager) {
    val keyboardVisible = WindowInsets.isImeVisible
    var wasVisible by remember { mutableStateOf(keyboardVisible) }
    LaunchedEffect(keyboardVisible) {
        if (wasVisible && !keyboardVisible) focusManager.clearFocus()
        wasVisible = keyboardVisible
    }
}

/** Pull to refresh fetches changes made in Home Assistant; without a remote there is nothing to fetch. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshableContent(
    enabled: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (enabled) {
        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) { content() }
    } else {
        content()
    }
}

@Composable
private fun EditItemDialogHost(
    state: ShoppingUiState,
    editedItemId: String?,
    actions: ShoppingActions,
    onClose: () -> Unit,
) {
    // Looked up only while a dialog is open.
    val editedItem = editedItemId?.let { id -> state.toBuy.find { it.id == id } ?: state.purchased.find { it.id == id } }
    if (editedItem != null) {
        EditItemDialog(
            item = editedItem,
            onDismiss = onClose,
            onSave = { name, quantity, unit ->
                actions.onSaveItem(editedItem, name, quantity, unit)
                onClose()
            },
            onDelete = {
                actions.onDeleteItem(editedItem)
                onClose()
            },
        )
    }
}
