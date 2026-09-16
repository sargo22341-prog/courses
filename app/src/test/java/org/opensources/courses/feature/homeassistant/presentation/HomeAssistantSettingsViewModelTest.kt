package org.opensources.courses.feature.homeassistant.presentation

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.opensources.courses.core.sync.SyncFailure
import org.opensources.courses.core.sync.SyncOutcome
import org.opensources.courses.feature.homeassistant.domain.ForgetHomeAssistantUseCase
import org.opensources.courses.feature.homeassistant.domain.HaTodoList
import org.opensources.courses.testing.FakeHaConfigRepository
import org.opensources.courses.testing.FakeHaListLinkRepository
import org.opensources.courses.testing.FakeHomeAssistantGateway
import org.opensources.courses.testing.FakeRemoteSyncEngine
import org.opensources.courses.testing.FakeShoppingListRepository
import org.opensources.courses.testing.MainDispatcherRule
import org.opensources.courses.testing.syncCoordinator

class HomeAssistantSettingsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val config = FakeHaConfigRepository()
    private val gateway = FakeHomeAssistantGateway().apply { lists["todo.maison"] = HaTodoList("todo.maison", "Maison", supportsDescription = true) }
    private val links = FakeHaListLinkRepository()
    private val lists = FakeShoppingListRepository()
    private val engine = FakeRemoteSyncEngine()

    private fun TestScope.viewModel(): HomeAssistantSettingsViewModel {
        val coordinator = syncCoordinator(engine, backgroundScope)
        return HomeAssistantSettingsViewModel(
            configRepository = config,
            gateway = gateway,
            linkRepository = links,
            listRepository = lists,
            syncCoordinator = coordinator,
            forgetHomeAssistant = ForgetHomeAssistantUseCase(config, links, coordinator),
        ).also { viewModel -> backgroundScope.launch(main.dispatcher) { viewModel.uiState.collect {} } }
    }

    @Test
    fun `first setup offers each existing list in turn, then is never asked again`() =
        runTest {
            val courses = lists.createList("Courses")
            val bricolage = lists.createList("Bricolage")
            val viewModel = viewModel()

            val first = viewModel.uiState.value
            assertEquals(courses.id, first.pickerList?.id)
            assertTrue(first.isSetupPicker)
            assertEquals(listOf("todo.maison"), first.pickerOptions.map { it.entityId })

            viewModel.closePicker(courses.id)
            assertEquals(bricolage.id, viewModel.uiState.value.pickerList?.id)
            assertFalse(config.config.value.listsSetupDone)

            viewModel.linkToExisting(bricolage.id, "todo.maison")

            assertNull(viewModel.uiState.value.pickerList)
            assertTrue(config.config.value.listsSetupDone)
            assertEquals(mapOf(bricolage.id to "todo.maison"), links.linked)
        }

    @Test
    fun `a list chosen by hand is not part of the first setup`() =
        runTest {
            config.setListsSetupDone()
            val courses = lists.createList("Courses")
            val viewModel = viewModel()
            assertNull(viewModel.uiState.value.pickerList)

            viewModel.openPicker(courses.id)
            assertEquals(courses.id, viewModel.uiState.value.pickerList?.id)
            assertFalse(viewModel.uiState.value.isSetupPicker)

            viewModel.createInHomeAssistant(courses.id)
            assertEquals(listOf(courses.id), links.created)
            assertNull(viewModel.uiState.value.pickerList)
        }

    @Test
    fun `an invalid address is reported and nothing is saved`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onUrlChange("pas une url")
            viewModel.onTokenChange("token")

            viewModel.save()

            assertEquals(HaActionStatus.Done(HaMessage.INVALID_URL), viewModel.uiState.value.connection)
            assertEquals("http://ha.local:8123", config.config.value.baseUrl)
            assertEquals("token", viewModel.tokenInput)
        }

    @Test
    fun `a saved address is shown normalized and the typed token is cleared`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onUrlChange("ha.nas.home:8123")
            viewModel.onTokenChange("new-token")

            viewModel.save()

            assertEquals("http://ha.nas.home:8123", viewModel.urlInput)
            assertEquals("", viewModel.tokenInput)
            assertEquals(HaActionStatus.Done(HaMessage.SAVED), viewModel.uiState.value.connection)
        }

    @Test
    fun `forgetting the connection clears the screen and offers the first setup again`() =
        runTest {
            config.setListsSetupDone()
            val courses = lists.createList("Courses")
            val viewModel = viewModel()
            viewModel.onTokenChange("typed")

            viewModel.forgetConnection()

            assertTrue(links.unlinkedAll)
            assertEquals("", viewModel.urlInput)
            assertEquals("", viewModel.tokenInput)
            assertEquals(HaActionStatus.Done(HaMessage.FORGOTTEN), viewModel.uiState.value.connection)
            assertFalse(viewModel.uiState.value.config.isConfigured)

            // Connecting again asks again what to do with the lists.
            viewModel.onUrlChange("https://ha.nas.home")
            viewModel.onTokenChange("token")
            viewModel.save()
            viewModel.setEnabled(true)
            assertEquals(courses.id, viewModel.uiState.value.pickerList?.id)
        }

    @Test
    fun `a synchronisation giving up refused changes says so`() =
        runTest {
            engine.outcome = SyncOutcome.Failure(SyncFailure.REJECTED)
            val viewModel = viewModel()

            viewModel.syncNow()

            assertEquals(HaActionStatus.Done(HaMessage.SYNC_REJECTED), viewModel.uiState.first().sync)
        }
}
