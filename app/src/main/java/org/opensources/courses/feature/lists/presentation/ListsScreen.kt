package org.opensources.courses.feature.lists.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.opensources.courses.R
import org.opensources.courses.core.designsystem.component.BackTopBar
import org.opensources.courses.feature.lists.domain.ShoppingList

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
    val snackbarHostState = remember { SnackbarHostState() }
    val warningText = stringResource(R.string.lists_delete_last)
    var dialog by remember { mutableStateOf<ListsDialog?>(null) }

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
            ExtendedFloatingActionButton(
                onClick = { dialog = ListsDialog.Create },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.lists_new)) },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(lists, key = { it.id }) { list ->
                ListRow(
                    list = list,
                    onOpen = { onOpenList(list.id) },
                    onRename = { dialog = ListsDialog.Rename(list) },
                    onSetDefault = { viewModel.setDefault(list.id) },
                    onDelete = { dialog = ListsDialog.Delete(list) },
                    modifier = Modifier.animateItem(),
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
                    viewModel.create(name, onOpenList)
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
            DeleteListDialog(
                listName = current.list.name,
                onConfirm = {
                    dialog = null
                    viewModel.delete(current.list.id)
                },
                onDismiss = { dialog = null },
            )
        null -> Unit
    }
}

@Composable
private fun ListRow(
    list: ShoppingList,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 20.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.lists_actions, list.name))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_rename)) }, onClick = {
                        menuOpen = false
                        onRename()
                    })
                    if (!list.isDefault) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.lists_set_default)) }, onClick = {
                            menuOpen = false
                            onSetDefault()
                        })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = {
                        menuOpen = false
                        onDelete()
                    })
                }
            }
        }
    }
}
