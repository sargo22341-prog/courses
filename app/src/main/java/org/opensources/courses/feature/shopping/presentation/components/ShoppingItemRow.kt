package org.opensources.courses.feature.shopping.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filter
import org.opensources.courses.R
import org.opensources.courses.feature.shopping.domain.ShoppingItem
import java.util.Locale

/**
 * Tap: check/uncheck. Long press: edit. Swipe left: delete. "−"/"+": quantity of an item to buy.
 * [locale] writes the quantity; [highlighted] lights the row up once, for an item just added.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShoppingItemRow(
    item: ShoppingItem,
    locale: Locale,
    onToggle: (ShoppingItem) -> Unit,
    onDelete: (ShoppingItem) -> Unit,
    onEdit: (ShoppingItem) -> Unit,
    onChangeQuantity: (ShoppingItem, increase: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
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
    val haptics = LocalHapticFeedback.current
    val toggle = {
        haptics.performHapticFeedback(if (item.isChecked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
        onToggle(item)
    }
    val editLabel = stringResource(R.string.shopping_edit_item, item.name)
    val deleteLabel = stringResource(R.string.action_delete)
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (item.isChecked) colors.background else colors.surfaceContainerLow, tween(CHECK_MILLIS), label = "row")
    val textColor by animateColorAsState(if (item.isChecked) colors.onSurfaceVariant else colors.onSurface, tween(CHECK_MILLIS), label = "name")
    val highlight = rememberHighlight(highlighted)
    val bounce = rememberCheckBounce(item.isChecked)
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = { SwipeDeleteBackground(dismissState) },
    ) {
        Surface(color = lerp(container, colors.primaryContainer, highlight.value), shape = MaterialTheme.shapes.medium) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = toggle, onLongClick = { onEdit(item) }, onLongClickLabel = editLabel)
                        .semantics {
                            customActions =
                                listOf(
                                    CustomAccessibilityAction(editLabel) { onEdit(item).let { true } },
                                    CustomAccessibilityAction(deleteLabel) { onDelete(item).let { true } },
                                )
                        }.padding(start = 4.dp, end = if (item.isChecked) 16.dp else 0.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = item.isChecked,
                    onCheckedChange = { toggle() },
                    modifier =
                        Modifier.graphicsLayer {
                            scaleX = bounce.value
                            scaleY = bounce.value
                        },
                )
                StrikethroughText(item.name, struck = item.isChecked, color = textColor, modifier = Modifier.weight(1f))
                if (item.isChecked) QuantityText(item, locale) else QuantityControls(item, locale, onChangeQuantity)
            }
        }
    }
}

private const val CHECK_MILLIS = 250

/** What the rows of a list do, gathered in one stable object rather than passed row by row. */
@Immutable
class ItemRowActions(
    val onToggle: (ShoppingItem) -> Unit = {},
    val onDelete: (ShoppingItem) -> Unit = {},
    val onEdit: (ShoppingItem) -> Unit = {},
    val onChangeQuantity: (ShoppingItem, increase: Boolean) -> Unit = { _, _ -> },
)
