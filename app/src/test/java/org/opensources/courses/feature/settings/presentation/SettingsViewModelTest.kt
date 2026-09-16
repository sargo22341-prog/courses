package org.opensources.courses.feature.settings.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.opensources.courses.testing.FakeAppLanguageRepository
import org.opensources.courses.testing.FakeAppPreferencesRepository
import org.opensources.courses.testing.FakeCatalogRepository
import org.opensources.courses.testing.FakeHaConfigRepository
import org.opensources.courses.testing.FakeRemoteSyncEngine
import org.opensources.courses.testing.MainDispatcherRule
import org.opensources.courses.testing.product
import org.opensources.courses.testing.syncCoordinator

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val preferences = FakeAppPreferencesRepository()
    private val catalog = FakeCatalogRepository(listOf(product("Lait")))

    private fun TestScope.viewModel(): SettingsViewModel =
        SettingsViewModel(
            preferences = preferences,
            languages = FakeAppLanguageRepository(),
            catalog = catalog,
            haConfig = FakeHaConfigRepository(),
            syncCoordinator = syncCoordinator(FakeRemoteSyncEngine(), backgroundScope),
        ).also { viewModel -> backgroundScope.launch(main.dispatcher) { viewModel.uiState.collect {} } }

    @Test
    fun `the history is on by default and can be turned off`() =
        runTest {
            val viewModel = viewModel()
            runCurrent()
            assertTrue(viewModel.uiState.value.history.enabled)

            viewModel.setHistoryEnabled(false)
            runCurrent()

            assertFalse(preferences.state.value.historyEnabled)
            assertFalse(viewModel.uiState.value.history.enabled)
        }

    @Test
    fun `clearing the history forgets every addition`() =
        runTest {
            catalog.recordUsage("id:Lait")
            val viewModel = viewModel()
            runCurrent()
            assertTrue(viewModel.uiState.value.history.hasHistory)

            viewModel.clearHistory()
            runCurrent()

            assertEquals(emptyMap<String, Int>(), catalog.usage)
            assertFalse(viewModel.uiState.value.history.hasHistory)
        }
}
