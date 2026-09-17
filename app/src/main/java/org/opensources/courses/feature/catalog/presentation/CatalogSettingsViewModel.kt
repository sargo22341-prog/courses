package org.opensources.courses.feature.catalog.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.opensources.courses.feature.catalog.domain.CatalogRepository
import javax.inject.Inject

data class CatalogUiState(
    val productCount: Int = 0,
)

@HiltViewModel
class CatalogSettingsViewModel
    @Inject
    constructor(
        repository: CatalogRepository,
    ) : ViewModel() {
        val uiState: StateFlow<CatalogUiState> =
            repository
                .observeProductCount()
                .map(::CatalogUiState)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogUiState())
    }
