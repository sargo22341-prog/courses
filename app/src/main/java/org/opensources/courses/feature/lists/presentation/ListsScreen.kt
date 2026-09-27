package org.opensources.courses.feature.lists.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import org.opensources.courses.feature.lists.presentation.components.ImportRemoteListDialog
import org.opensources.courses.feature.lists.presentation.components.ListDragHandle
import org.opensources.courses.feature.lists.presentation.components.ListDragState
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
    val canImportRemoteList by viewModel.canImportRemoteList.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<ListsDialog?>(null) }
    val listState = rememberLazyListState()
    val drag = rememberListDragState(listState)
    LaunchedEffect(lists) { drag.sync(lists) }
    CreatedListOpener(createdListId, onOpened = viewModel::createdListOpened, onOpenList = onOpenList)
    LastListWarning(lastListWarning, snackbarHostState, onShown = viewModel::lastListWarningShown)

    ListsScreen(
        drag = drag,
        listState = listState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onNewList = { dialog = ListsDialog.Create },
        rowActions = { list ->
            ListRowActions(
                onOpen = { onOpenList(list.id) },
                onRename = { dialog = ListsDialog.Rename(list) },
                onSetDefault = { viewModel.setDefault(list.id) },
                onDelete = { dialog = ListsDialog.Delete(list) },
                onMove = { offset -> viewModel.move(list.id, offset) },
            )
        },
        onDrop = viewModel::reorder,
    )
    ListsDialogHost(
        dialog = dialog,
        onClose = { dialog = null },
        onCreate = viewModel::create,
        onRename = viewModel::rename,
        onDelete = viewModel::delete,
        onImport = if (canImportRemoteList) viewModel::openImport else null,
    )
    if (importState != ListImportUiState.Closed) {
        ImportRemoteListDialog(
            state = importState,
            onImport = viewModel::importList,
            onRetry = viewModel::openImport,
            onDismiss = viewModel::closeImport,
        )
    }
}

/** Opened once: the event is cleared before navigating, so coming back does not open it again. */
@Composable
private fun CreatedListOpener(
    createdListId: String?,
    onOpened: () -> Unit,
    onOpenList: (String) -> Unit,
) {
    LaunchedEffect(createdListId) {
        createdListId?.let { id ->
            onOpened()
            onOpenList(id)
        }
    }
}

/** The last list cannot be deleted: a snackbar tells why. */
@Composable
private fun LastListWarning(
    shown: Boolean,
    snackbarHostState: SnackbarHostState,
    onShown: () -> Unit,
) {
    val warningText = stringResource(R.string.lists_delete_last)
    LaunchedEffect(shown) {
        if (shown) {
            snackbarHostState.showSnackbar(warningText)
            onShown()
        }
    }
}

/** The lists in the order of [drag], each row with the [rowActions] of its list. */
@Composable
private fun ListsScreen(
    drag: ListDragState,
    listState: LazyListState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onNewList: () -> Unit,
    rowActions: (ShoppingList) -> ListRowActions,
    onDrop: (List<String>) -> Unit,
) {
    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.lists_title), onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            val newList = stringResource(R.string.lists_new)
            ExtendedFloatingActionButton(
                onClick = onNewList,
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
                    actions = rowActions(list),
                    dragHandle = { ListDragHandle(drag, list.id, onDrop = onDrop) },
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
}

/**
 * The dialog opened from the lists screen, if any; [onClose] closes it before its action runs.
 * [onImport], when given, is offered in the creation dialog.
 */
@Composable
private fun ListsDialogHost(
    dialog: ListsDialog?,
    onClose: () -> Unit,
    onCreate: (name: String) -> Unit,
    onRename: (listId: String, name: String) -> Unit,
    onDelete: (listId: String) -> Unit,
    onImport: (() -> Unit)?,
) {
    when (dialog) {
        ListsDialog.Create ->
            ListNameDialog(
                title = stringResource(R.string.lists_new),
                initialName = "",
                confirmLabel = stringResource(R.string.action_create),
                onConfirm = { name ->
                    onClose()
                    onCreate(name)
                },
                onDismiss = onClose,
                onImport =
                    onImport?.let { import ->
                        {
                            onClose()
                            import()
                        }
                    },
            )
        is ListsDialog.Rename ->
            ListNameDialog(
                title = stringResource(R.string.lists_rename_title),
                initialName = dialog.list.name,
                confirmLabel = stringResource(R.string.action_save),
                onConfirm = { name ->
                    onClose()
                    onRename(dialog.list.id, name)
                },
                onDismiss = onClose,
            )
        is ListsDialog.Delete ->
            ConfirmDialog(
                title = stringResource(R.string.lists_delete_title, dialog.list.name),
                text = stringResource(R.string.lists_delete_body),
                confirmLabel = stringResource(R.string.action_delete),
                onConfirm = {
                    onClose()
                    onDelete(dialog.list.id)
                },
                onDismiss = onClose,
            )
        null -> Unit
    }
}
