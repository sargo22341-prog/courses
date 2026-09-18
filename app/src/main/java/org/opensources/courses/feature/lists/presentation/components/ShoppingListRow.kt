package org.opensources.courses.feature.lists.presentation.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.opensources.courses.R
import org.opensources.courses.feature.lists.domain.ShoppingList

/** What a list row does; [onMove] takes -1 (up) or 1 (down). */
@Immutable
internal class ListRowActions(
    val onOpen: () -> Unit,
    val onRename: () -> Unit,
    val onSetDefault: () -> Unit,
    val onDelete: () -> Unit,
    val onMove: (offset: Int) -> Unit,
)

/**
 * A list on the lists screen. [dragHandle] sits at its start; [lifted] while it is dragged: it rises
 * above the others with a shadow. "Monter" and "Descendre" in its menu do what a drag does, for those
 * who cannot drag.
 */
@Composable
internal fun ShoppingListRow(
    list: ShoppingList,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    lifted: Boolean,
    actions: ListRowActions,
    dragHandle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val elevation by animateDpAsState(if (lifted) LIFTED_ELEVATION else 0.dp, label = "lift")
    val scale by animateFloatAsState(if (lifted) LIFTED_SCALE else 1f, label = "lift_scale")
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        shadowElevation = elevation,
        modifier =
            modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = actions.onOpen).padding(end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dragHandle()
            Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(list.name, style = MaterialTheme.typography.titleMedium)
                val badges =
                    listOfNotNull(
                        stringResource(R.string.lists_default_badge).takeIf { list.isDefault },
                        stringResource(R.string.lists_synced_badge).takeIf { list.isSynchronized },
                    )
                if (badges.isNotEmpty()) {
                    Text(badges.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            ListMenu(list, canMoveUp, canMoveDown, actions)
        }
    }
}

@Composable
private fun ListMenu(
    list: ShoppingList,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    actions: ListRowActions,
) {
    var menuOpen by remember { mutableStateOf(false) }
    fun item(action: () -> Unit): () -> Unit =
        {
            menuOpen = false
            action()
        }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.lists_actions, list.name))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = item(actions.onRename))
            if (!list.isDefault) {
                DropdownMenuItem(text = { Text(stringResource(R.string.lists_set_default)) }, onClick = item(actions.onSetDefault))
            }
            if (canMoveUp) {
                DropdownMenuItem(text = { Text(stringResource(R.string.lists_move_up)) }, onClick = item { actions.onMove(-1) })
            }
            if (canMoveDown) {
                DropdownMenuItem(text = { Text(stringResource(R.string.lists_move_down)) }, onClick = item { actions.onMove(1) })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = item(actions.onDelete))
        }
    }
}

private val LIFTED_ELEVATION = 8.dp
private const val LIFTED_SCALE = 1.02f
