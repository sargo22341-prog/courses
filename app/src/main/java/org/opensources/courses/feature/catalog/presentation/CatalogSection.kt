package org.opensources.courses.feature.catalog.presentation

import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import org.opensources.courses.R
import org.opensources.courses.core.designsystem.component.SettingsCard
import org.opensources.courses.core.designsystem.component.StatusText
import org.opensources.courses.feature.catalog.domain.CatalogSyncResult
import org.opensources.courses.feature.catalog.domain.CatalogSyncStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)

@Composable
fun CatalogSection(
    state: CatalogUiState,
    onSyncNow: () -> Unit,
) {
    SettingsCard(stringResource(R.string.catalog_title)) {
        val lastSync = state.lastSyncAt
        Text(
            text =
                if (lastSync == null) {
                    stringResource(R.string.catalog_never_synced)
                } else {
                    stringResource(R.string.catalog_last_sync, remember(lastSync) { formatDate(lastSync) })
                },
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = LocalResources.current.getQuantityString(R.plurals.catalog_products, state.productCount, state.productCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val running = state.status == CatalogSyncStatus.Running
        FilledTonalButton(onClick = onSyncNow, enabled = !running) {
            Text(stringResource(if (running) R.string.catalog_syncing else R.string.catalog_sync_now))
        }
        (state.status as? CatalogSyncStatus.Finished)?.let { finished -> ResultText(finished.result) }
        Text(
            text = stringResource(R.string.catalog_attribution),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultText(result: CatalogSyncResult) {
    when (result) {
        is CatalogSyncResult.Updated -> StatusText(stringResource(R.string.catalog_updated), isError = false)
        CatalogSyncResult.UpToDate, CatalogSyncResult.NotNeeded -> StatusText(stringResource(R.string.catalog_up_to_date), isError = false)
        CatalogSyncResult.Offline -> StatusText(stringResource(R.string.catalog_offline), isError = true)
        CatalogSyncResult.Failed -> StatusText(stringResource(R.string.catalog_failed), isError = true)
    }
}

private fun formatDate(instant: Instant): String = DateFormatter.format(instant.atZone(ZoneId.systemDefault()))
