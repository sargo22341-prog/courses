package org.opensources.courses.feature.shopping.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.opensources.courses.R
import org.opensources.courses.feature.shopping.domain.ShoppingItem
import org.opensources.courses.feature.shopping.presentation.ShoppingUiState

@Composable
fun ShoppingListContent(
    state: ShoppingUiState,
    onToggleItem: (ShoppingItem) -> Unit,
    onDeleteItem: (ShoppingItem) -> Unit,
    onEditItem: (ShoppingItem) -> Unit,
    onRequestDeletePurchased: () -> Unit,
) {
    if (state.isLoading) return
    if (state.toBuy.isEmpty() && state.purchased.isEmpty()) {
        EmptyState()
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (state.toBuy.isEmpty()) {
            item(key = "all_purchased") {
                Text(
                    text = stringResource(R.string.shopping_all_purchased),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp).animateItem(),
                    textAlign = TextAlign.Center,
                )
            }
        }
        val sections = state.toBuySections
        if (sections == null) {
            itemRows(state.toBuy, onToggleItem, onDeleteItem, onEditItem)
        } else {
            sections.forEach { section ->
                item(key = "category_${section.category.name}", contentType = CATEGORY_HEADER) {
                    CategoryHeader(section.category, Modifier.animateItem())
                }
                itemRows(section.items, onToggleItem, onDeleteItem, onEditItem)
            }
        }
        if (state.purchased.isNotEmpty() && !state.hidePurchased) {
            item(key = "purchased_header") { PurchasedHeader(onRequestDeletePurchased, Modifier.animateItem()) }
            itemRows(state.purchased, onToggleItem, onDeleteItem, onEditItem)
        }
    }
}

private fun LazyListScope.itemRows(
    items: List<ShoppingItem>,
    onToggleItem: (ShoppingItem) -> Unit,
    onDeleteItem: (ShoppingItem) -> Unit,
    onEditItem: (ShoppingItem) -> Unit,
) {
    items(items, key = { it.id }) { item ->
        ShoppingItemRow(item, onToggleItem, onDeleteItem, onEditItem, Modifier.animateItem())
    }
}

@Composable
private fun PurchasedHeader(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth().padding(start = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.shopping_section_purchased),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.shopping_delete_purchased),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Scrollable, so that pull to refresh also works on an empty list. */
@Composable
private fun EmptyState() {
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "empty") {
            Box(Modifier.fillParentMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.shopping_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.shopping_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private const val CATEGORY_HEADER = "category_header"
