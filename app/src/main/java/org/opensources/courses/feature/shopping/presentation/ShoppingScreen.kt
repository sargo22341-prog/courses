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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import org.opensources.courses.feature.catalog.domain.ProductSuggestion
import org.opensources.courses.feature.shopping.domain.ShoppingItem
import org.opensources.courses.feature.shopping.presentation.components.AddItemField
import org.opensources.courses.feature.shopping.presentation.components.EditItemDialog
import org.opensources.courses.feature.shopping.presentation.components.PurchasedFooter
import org.opensources.courses.feature.shopping.presentation.components.ShoppingListContent
import org.opensources.courses.feature.shopping.presentation.components.SuggestionsPanel
import org.opensources.courses.feature.shopping.presentation.components.SyncIndicator

@Composable
fun ShoppingRoute(
    onOpenLists: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ShoppingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ShoppingScreen(
        state = state,
        query = viewModel.query,
        actions =
            ShoppingActions(
                onQueryChange = viewModel::onQueryChange,
                onSubmitQuery = viewModel::onSubmitQuery,
                onSuggestionSelected = viewModel::onSuggestionSelected,
                onAddCustomItem = viewModel::onAddCustomItem,
                onToggleItem = viewModel::onToggleItem,
                onDeleteItem = viewModel::onDeleteItem,
                onSaveItem = viewModel::onSaveItem,
                onToggleHidePurchased = viewModel::onToggleHidePurchased,
                onDeletePurchased = viewModel::onDeletePurchased,
                onOpenLists = onOpenLists,
                onOpenSettings = onOpenSettings,
            ),
    )
}

class ShoppingActions(
    val onQueryChange: (String) -> Unit = {},
    val onSubmitQuery: () -> Unit = {},
    val onSuggestionSelected: (ProductSuggestion) -> Unit = {},
    val onAddCustomItem: () -> Unit = {},
    val onToggleItem: (ShoppingItem) -> Unit = {},
    val onDeleteItem: (ShoppingItem) -> Unit = {},
    val onSaveItem: (ShoppingItem, String, Double, String?) -> Unit = { _, _, _, _ -> },
    val onToggleHidePurchased: () -> Unit = {},
    val onDeletePurchased: () -> Unit = {},
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
    val searching = query.isNotBlank()
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
                    onDeletePurchased = actions.onDeletePurchased,
                )
            }
        },
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
                    offersCustomItem = state.offersCustomItem(query),
                    onSuggestionSelected = actions.onSuggestionSelected,
                    onAddCustomItem = actions.onAddCustomItem,
                )
            } else {
                ShoppingListContent(
                    state = state,
                    onToggleItem = actions.onToggleItem,
                    onDeleteItem = actions.onDeleteItem,
                    onEditItem = { editedItemId = it.id },
                )
            }
        }
    }
    val editedItem = (state.toBuy + state.purchased).firstOrNull { it.id == editedItemId }
    if (editedItem != null) {
        EditItemDialog(
            item = editedItem,
            onDismiss = { editedItemId = null },
            onSave = { name, quantity, unit ->
                actions.onSaveItem(editedItem, name, quantity, unit)
                editedItemId = null
            },
            onDelete = {
                actions.onDeleteItem(editedItem)
                editedItemId = null
            },
        )
    }
}
