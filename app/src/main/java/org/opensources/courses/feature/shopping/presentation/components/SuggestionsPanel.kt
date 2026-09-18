package org.opensources.courses.feature.shopping.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.opensources.courses.R
import org.opensources.courses.feature.catalog.domain.ProductSuggestion
import org.opensources.courses.feature.shopping.domain.ItemEntry
import org.opensources.courses.feature.shopping.domain.QuantityFormatter

/** [entry]: what the suggestions were searched for; its quantity, if any, is shown on every choice. */
@Composable
fun SuggestionsPanel(
    entry: ItemEntry,
    suggestions: List<ProductSuggestion>,
    offersCustomItem: Boolean,
    onSuggestionSelected: (ProductSuggestion) -> Unit,
    onAddCustomItem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // A lazy list keeps its scroll anchored on the previous first item; the best match must
    // always be visible at the top after each keystroke.
    LaunchedEffect(suggestions) { listState.scrollToItem(0) }
    val quantity = quantityLabel(entry)
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(suggestions, key = { it.productId }) { suggestion ->
            SuggestionRow(onClick = { onSuggestionSelected(suggestion) }, modifier = Modifier.animateItem()) {
                SuggestionLabel(suggestion, Modifier.weight(1f))
                quantity?.let { QuantityBadge(it) }
            }
        }
        if (offersCustomItem) {
            item(key = "custom") {
                SuggestionRow(onClick = onAddCustomItem, modifier = Modifier.animateItem()) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = stringResource(R.string.shopping_add_custom, entry.name),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    quantity?.let { QuantityBadge(it) }
                }
            }
        }
    }
}

/** "× 2" for a count, "500 g" for a measure; null when no quantity was typed. */
@Composable
private fun quantityLabel(entry: ItemEntry): String? {
    val quantity = entry.quantity ?: return null
    val locale = LocalConfiguration.current.locales[0]
    val formatted = QuantityFormatter.format(quantity, entry.unit, locale)
    return if (entry.unit == null) stringResource(R.string.shopping_quantity_times, formatted) else formatted
}

/** The quantity typed, which the product will be added with. */
@Composable
private fun QuantityBadge(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier =
            Modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Product name, and its catalog category when there is one. */
@Composable
internal fun SuggestionLabel(
    suggestion: ProductSuggestion,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(suggestion.name, style = MaterialTheme.typography.bodyLarge)
        suggestion.category?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SuggestionRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}
