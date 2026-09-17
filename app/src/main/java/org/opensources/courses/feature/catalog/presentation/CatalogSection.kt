package org.opensources.courses.feature.catalog.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import org.opensources.courses.R
import org.opensources.courses.core.designsystem.component.SettingsCard

/** The catalog ships with the application: nothing to download, nothing to configure, only what it holds. */
@Composable
fun CatalogSection(state: CatalogUiState) {
    SettingsCard(stringResource(R.string.catalog_title)) {
        Text(
            text = LocalResources.current.getQuantityString(R.plurals.catalog_products, state.productCount, state.productCount),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.catalog_attribution),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
