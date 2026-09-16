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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.opensources.courses.R
import org.opensources.courses.feature.shopping.domain.ItemSection
import org.opensources.courses.feature.shopping.domain.ShoppingItem
import java.util.Locale

/**
 * Takes only what the list shows, not the whole screen state: a change of the sync state or of the
 * suggestions leaves these parameters unchanged, and the list is not recomposed.
 *
 * @param toBuySections [toBuy] grouped by shop section, or null to show them as they come.
 */
@Composable
fun ShoppingListContent(
    isLoading: Boolean,
    toBuy: List<ShoppingItem>,
    toBuySections: List<ItemSection>?,
    purchased: List<ShoppingItem>,
    hidePurchased: Boolean,
    onToggleItem: (ShoppingItem) -> Unit,
    onDeleteItem: (ShoppingItem) -> Unit,
    onEditItem: (ShoppingItem) -> Unit,
    onRequestDeletePurchased: () -> Unit,
) {
    if (isLoading) return
    if (toBuy.isEmpty() && purchased.isEmpty()) {
        EmptyState()
        return
    }
    // Read once for every row.
    val locale = LocalConfiguration.current.locales[0]
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (toBuy.isEmpty()) {
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
        if (toBuySections == null) {
            itemRows(toBuy, locale, onToggleItem, onDeleteItem, onEditItem)
        } else {
            toBuySections.forEach { section ->
                item(key = "category_${section.category.name}", contentType = CATEGORY_HEADER) {
                    CategoryHeader(section.category, Modifier.animateItem())
                }
                itemRows(section.items, locale, onToggleItem, onDeleteItem, onEditItem)
            }
        }
        if (purchased.isNotEmpty() && !hidePurchased) {
            item(key = "purchased_header") { PurchasedHeader(onRequestDeletePurchased, Modifier.animateItem()) }
            itemRows(purchased, locale, onToggleItem, onDeleteItem, onEditItem)
        }
    }
}

private fun LazyListScope.itemRows(
    items: List<ShoppingItem>,
    locale: Locale,
    onToggleItem: (ShoppingItem) -> Unit,
    onDeleteItem: (ShoppingItem) -> Unit,
    onEditItem: (ShoppingItem) -> Unit,
) {
    items(items, key = { it.id }, contentType = { ITEM_ROW }) { item ->
        ShoppingItemRow(item, locale, onToggleItem, onDeleteItem, onEditItem, Modifier.animateItem())
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
private const val ITEM_ROW = "item_row"
