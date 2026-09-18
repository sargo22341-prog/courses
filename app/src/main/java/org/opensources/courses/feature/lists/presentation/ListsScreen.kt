package org.opensources.courses.feature.lists.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.opensources.courses.R
import org.opensources.courses.core.designsystem.component.BackTopBar
import org.opensources.courses.core.designsystem.component.ConfirmDialog
import org.opensources.courses.feature.lists.domain.ShoppingList
import org.opensources.courses.feature.lists.presentation.components.ListDragHandle
import org.opensources.courses.feature.lists.presentation.components.ListRowActions
import org.opensources.courses.feature.lists.presentation.components.ShoppingListRow
import org.opensources.courses.feature.lists.presentation.components.rememberListDragState

private sealed interface ListsDialog {
    data object Create : ListsDialog

    data class Rename(
        val list: ShoppingList,
    ) : ListsDialog

    data class Delete(
        val list: ShoppingList,
    ) : ListsDialog
}

@Composable
fun ListsRoute(
    onBack: () -> Unit,
    onOpenList: (String) -> Unit,
    viewModel: ListsViewModel = hiltViewModel(),
) {
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val lastListWarning by viewModel.lastListWarning.collectAsStateWithLifecycle()
    val createdListId by viewModel.createdListId.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val warningText = stringResource(R.string.lists_delete_last)
    var dialog by remember { mutableStateOf<ListsDialog?>(null) }
    val listState = rememberLazyListState()
    val drag = rememberListDragState(listState)
    LaunchedEffect(lists) { drag.sync(lists) }

    // Opened once: the event is cleared before navigating, so coming back does not open it again.
    LaunchedEffect(createdListId) {
        createdListId?.let { id ->
            viewModel.createdListOpened()
            onOpenList(id)
        }
    }

    LaunchedEffect(lastListWarning) {
        if (lastListWarning) {
            snackbarHostState.showSnackbar(warningText)
            viewModel.lastListWarningShown()
        }
    }

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.lists_title), onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            val newList = stringResource(R.string.lists_new)
            ExtendedFloatingActionButton(
                onClick = { dialog = ListsDialog.Create },
                // Material hides the text from accessibility services: the icon carries the label.
                icon = { Icon(Icons.Filled.Add, contentDescription = newList) },
                text = { Text(newList) },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(drag.order, key = { _, list -> list.id }) { index, list ->
                val lifted = drag.draggedId == list.id
                ShoppingListRow(
                    list = list,
                    canMoveUp = index > 0,
                    canMoveDown = index < drag.order.lastIndex,
                    lifted = lifted,
                    actions =
                        ListRowActions(
                            onOpen = { onOpenList(list.id) },
                            onRename = { dialog = ListsDialog.Rename(list) },
                            onSetDefault = { viewModel.setDefault(list.id) },
                            onDelete = { dialog = ListsDialog.Delete(list) },
                            onMove = { offset -> viewModel.move(list.id, offset) },
                        ),
                    dragHandle = { ListDragHandle(drag, list.id, onDrop = viewModel::reorder) },
                    // The held row follows the finger above the others; they make room for it.
                    modifier =
                        if (lifted) {
                            Modifier.zIndex(1f).graphicsLayer { translationY = drag.offsetOf(list.id) }
                        } else {
                            Modifier.animateItem()
                        },
                )
            }
        }
    }

    when (val current = dialog) {
        ListsDialog.Create ->
            ListNameDialog(
                title = stringResource(R.string.lists_new),
                initialName = "",
                confirmLabel = stringResource(R.string.action_create),
                onConfirm = { name ->
                    dialog = null
                    viewModel.create(name)
                },
                onDismiss = { dialog = null },
            )
        is ListsDialog.Rename ->
            ListNameDialog(
                title = stringResource(R.string.lists_rename_title),
                initialName = current.list.name,
                confirmLabel = stringResource(R.string.action_save),
                onConfirm = { name ->
                    dialog = null
                    viewModel.rename(current.list.id, name)
                },
                onDismiss = { dialog = null },
            )
        is ListsDialog.Delete ->
            ConfirmDialog(
                title = stringResource(R.string.lists_delete_title, current.list.name),
                text = stringResource(R.string.lists_delete_body),
                confirmLabel = stringResource(R.string.action_delete),
                onConfirm = {
                    dialog = null
                    viewModel.delete(current.list.id)
                },
                onDismiss = { dialog = null },
            )
        null -> Unit
    }
}
