package org.opensources.courses.feature.shopping.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.opensources.courses.R
import org.opensources.courses.core.designsystem.component.SyncIndicator
import org.opensources.courses.core.sync.SyncSnapshot

/** The name of the list shown, its synchronisation state, and the way to the lists and the settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShoppingTopBar(
    listName: String,
    sync: SyncSnapshot,
    onOpenLists: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(listName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                SyncIndicator(sync)
            }
        },
        actions = {
            IconButton(onClick = onOpenLists) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.shopping_open_lists))
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.shopping_open_settings))
            }
        },
    )
}
