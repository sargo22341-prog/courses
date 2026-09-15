package org.opensources.courses.feature.catalog.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.opensources.courses.feature.catalog.domain.CatalogRepository
import org.opensources.courses.feature.catalog.domain.CatalogSyncManager
import org.opensources.courses.feature.catalog.domain.CatalogSyncStateStore
import org.opensources.courses.feature.catalog.domain.CatalogSyncStatus
import org.opensources.courses.feature.language.domain.AppLanguageRepository
import java.time.Instant
import javax.inject.Inject

/** @property languagePending the downloaded catalog is still in the previous language of the app. */
data class CatalogUiState(
    val lastSyncAt: Instant? = null,
    val productCount: Int = 0,
    val status: CatalogSyncStatus = CatalogSyncStatus.Idle,
    val languagePending: Boolean = false,
)

@HiltViewModel
class CatalogSettingsViewModel
    @Inject
    constructor(
        stateStore: CatalogSyncStateStore,
        repository: CatalogRepository,
        languages: AppLanguageRepository,
        private val syncManager: CatalogSyncManager,
    ) : ViewModel() {
        val uiState: StateFlow<CatalogUiState> =
            combine(stateStore.info, repository.observeProductCount(), syncManager.status, languages.language) { info, count, status, language ->
                CatalogUiState(info.lastSyncAt, count, status, languagePending = info.lastSyncAt != null && info.language != language)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogUiState())

        fun forceSync() {
            viewModelScope.launch { syncManager.forceSync() }
        }
    }
