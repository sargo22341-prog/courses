package org.opensources.courses.feature.shopping.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filter
import org.opensources.courses.R
import org.opensources.courses.feature.shopping.domain.QuantityFormatter
import org.opensources.courses.feature.shopping.domain.ShoppingItem

/** Tap: check/uncheck. Long press: edit. Swipe left: delete. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShoppingItemRow(
    item: ShoppingItem,
    onToggle: (ShoppingItem) -> Unit,
    onDelete: (ShoppingItem) -> Unit,
    onEdit: (ShoppingItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    val currentItem by rememberUpdatedState(item)
    val currentOnDelete by rememberUpdatedState(onDelete)
    LaunchedEffect(dismissState) {
        // The state is saved under the item's key: a row shown again after "Annuler" comes back
        // swiped away. It is put back first, so only a new swipe deletes the item.
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        snapshotFlow { dismissState.currentValue }
            .filter { it == SwipeToDismissBoxValue.EndToStart }
            .collect { currentOnDelete(currentItem) }
    }
    val editLabel = stringResource(R.string.shopping_edit_item, item.name)
    val deleteLabel = stringResource(R.string.action_delete)
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = { DeleteBackground() },
    ) {
        Surface(
            color = if (item.isChecked) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = { onToggle(item) }, onLongClick = { onEdit(item) }, onLongClickLabel = editLabel)
                        .semantics {
                            customActions =
                                listOf(
                                    CustomAccessibilityAction(editLabel) { onEdit(item).let { true } },
                                    CustomAccessibilityAction(deleteLabel) { onDelete(item).let { true } },
                                )
                        }.padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = item.isChecked, onCheckedChange = { onToggle(item) })
                Text(
                    text = item.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (item.isChecked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = QuantityFormatter.format(item.quantity, item.unit, LocalConfiguration.current.locales[0]),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeleteBackground() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium)
                .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Row(horizontalArrangement = Arrangement.End) {
            Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}
