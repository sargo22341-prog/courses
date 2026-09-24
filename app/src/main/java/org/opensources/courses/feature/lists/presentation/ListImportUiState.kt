package org.opensources.courses.feature.lists.presentation

import org.opensources.courses.core.sync.RemoteListChoice
import org.opensources.courses.core.sync.SyncFailure

/** The dialog importing a list of the remote (Home Assistant) instead of creating an empty one. */
sealed interface ListImportUiState {
    data object Closed : ListImportUiState

    data object Loading : ListImportUiState

    data class Choosing(
        val lists: List<RemoteListChoice>,
    ) : ListImportUiState

    data class Failed(
        val reason: SyncFailure,
    ) : ListImportUiState
}
