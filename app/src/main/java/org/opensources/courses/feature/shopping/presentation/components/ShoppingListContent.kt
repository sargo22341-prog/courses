package org.opensources.courses.feature.shopping.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.opensources.courses.R
import org.opensources.courses.feature.shopping.domain.ItemSection
import org.opensources.courses.feature.shopping.domain.ShoppingItem

/**
 * Takes only what the list shows, not the whole screen state: a change of the sync state or of the
 * suggestions leaves these parameters unchanged, and the list is not recomposed.
 *
 * @param toBuySections [toBuy] grouped by shop section, or null to show them as they come.
 * @param highlightedItemId an item just added: the list scrolls to it if needed, lights it up, then
 * calls [onHighlightShown].
 */
@Composable
fun ShoppingListContent(
    isLoading: Boolean,
    toBuy: List<ShoppingItem>,
    toBuySections: List<ItemSection>?,
    purchased: List<ShoppingItem>,
    hidePurchased: Boolean,
    actions: ItemRowActions,
    onRequestDeletePurchased: () -> Unit,
    modifier: Modifier = Modifier,
    highlightedItemId: String? = null,
    onHighlightShown: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
) {
    if (isLoading) return
    if (toBuy.isEmpty() && purchased.isEmpty()) {
        EmptyState(modifier)
        return
    }
    val rows = remember(toBuy, toBuySections, purchased, hidePurchased) { listRows(toBuy, toBuySections, purchased, hidePurchased) }
    ScrollToHighlight(listState, rows, highlightedItemId, onHighlightShown)
    // Read once for every row.
    val locale = LocalConfiguration.current.locales[0]
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(rows, key = { it.key }, contentType = { it.contentType }) { row ->
            val animated = Modifier.animateItem()
            when (row) {
                ListRow.AllPurchased ->
                    AppearingStatus(Icons.Filled.CheckCircle, stringResource(R.string.shopping_all_purchased), animated.padding(vertical = 24.dp))
                is ListRow.CategoryTitle -> CategoryHeader(row.section.category, animated)
                ListRow.PurchasedTitle -> PurchasedHeader(onRequestDeletePurchased, animated)
                is ListRow.Item ->
                    ShoppingItemRow(
                        item = row.item,
                        locale = locale,
                        onToggle = actions.onToggle,
                        onDelete = actions.onDelete,
                        onEdit = actions.onEdit,
                        onChangeQuantity = actions.onChangeQuantity,
                        modifier = animated,
                        highlighted = row.item.id == highlightedItemId,
                    )
            }
        }
    }
}

/** Scrolls only when the item is out of sight: the list does not jump for an item already shown. */
@Composable
private fun ScrollToHighlight(
    listState: LazyListState,
    rows: List<ListRow>,
    highlightedItemId: String?,
    onHighlightShown: () -> Unit,
) {
    val currentOnHighlightShown by rememberUpdatedState(onHighlightShown)
    LaunchedEffect(highlightedItemId, rows) {
        val id = highlightedItemId ?: return@LaunchedEffect
        // The item shows up with the next emission of the list: until then, wait for it.
        val index = rows.indexOfFirst { it is ListRow.Item && it.item.id == id }
        if (index < 0) return@LaunchedEffect
        val layout = listState.layoutInfo
        val shown = layout.visibleItemsInfo.any { it.index == index && it.offset >= 0 && it.offset + it.size <= layout.viewportEndOffset }
        if (!shown) listState.animateScrollToItem(index)
        currentOnHighlightShown()
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
private fun EmptyState(modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize()) {
        item(key = "empty") {
            Box(Modifier.fillParentMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                AppearingStatus(
                    icon = Icons.Filled.ShoppingCart,
                    title = stringResource(R.string.shopping_empty_title),
                    body = stringResource(R.string.shopping_empty_body),
                )
            }
        }
    }
}
