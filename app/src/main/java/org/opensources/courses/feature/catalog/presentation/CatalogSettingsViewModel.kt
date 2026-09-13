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
import java.time.Instant
import javax.inject.Inject

data class CatalogUiState(
    val lastSyncAt: Instant? = null,
    val productCount: Int = 0,
    val status: CatalogSyncStatus = CatalogSyncStatus.Idle,
)

@HiltViewModel
class CatalogSettingsViewModel
    @Inject
    constructor(
        stateStore: CatalogSyncStateStore,
        repository: CatalogRepository,
        private val syncManager: CatalogSyncManager,
    ) : ViewModel() {
        val uiState: StateFlow<CatalogUiState> =
            combine(stateStore.info, repository.observeProductCount(), syncManager.status) { info, count, status ->
                CatalogUiState(info.lastSyncAt, count, status)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogUiState())

        fun forceSync() {
            viewModelScope.launch { syncManager.forceSync() }
        }
    }
