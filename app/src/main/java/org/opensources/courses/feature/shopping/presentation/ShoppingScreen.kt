package org.opensources.courses.feature.shopping.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.opensources.courses.R
import org.opensources.courses.core.designsystem.component.SyncIndicator
import org.opensources.courses.feature.catalog.domain.ProductSuggestion
import org.opensources.courses.feature.shopping.domain.ShoppingItem
import org.opensources.courses.feature.shopping.presentation.components.AddItemField
import org.opensources.courses.feature.shopping.presentation.components.DeletePurchasedDialog
import org.opensources.courses.feature.shopping.presentation.components.EditItemDialog
import org.opensources.courses.feature.shopping.presentation.components.PurchasedFooter
import org.opensources.courses.feature.shopping.presentation.components.ShoppingListContent
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
    val onToggleHidePurchased: () -> Unit = {},
    val onDeletePurchased: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onOpenLists: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
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
    state.pendingDeletion?.let { DeletionUndoOffer(it, snackbarHostState, actions) }
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.ShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text(state.listName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        SyncIndicator(state.sync)
                    }
                },
                actions = {
                    IconButton(onClick = actions.onOpenLists) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.shopping_open_lists))
                    }
                    IconButton(onClick = actions.onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.shopping_open_settings))
                    }
                },
            )
        },
        bottomBar = {
            AnimatedVisibility(visible = state.purchased.isNotEmpty() && !searching) {
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
            )
            if (searching) {
                SuggestionsPanel(
                    query = query,
                    suggestions = state.suggestions,
                    offersCustomItem = remember(query, state.suggestions) { state.offersCustomItem(query) },
                    onSuggestionSelected = actions.onSuggestionSelected,
                    onAddCustomItem = actions.onAddCustomItem,
                )
            } else {
                RefreshableContent(enabled = state.sync.remoteEnabled, isRefreshing = state.isRefreshing, onRefresh = actions.onRefresh) {
                    ShoppingListContent(
                        isLoading = state.isLoading,
                        toBuy = state.toBuy,
                        toBuySections = state.toBuySections,
                        purchased = state.purchased,
                        hidePurchased = state.hidePurchased,
                        onToggleItem = actions.onToggleItem,
                        onDeleteItem = actions.onDeleteItem,
                        onEditItem = { editedItemId = it.id },
                        onRequestDeletePurchased = { confirmDeletePurchased = true },
                    )
                }
            }
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
