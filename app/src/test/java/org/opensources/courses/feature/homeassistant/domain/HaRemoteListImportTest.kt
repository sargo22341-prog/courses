package org.opensources.courses.feature.homeassistant.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.courses.core.model.SyncStatus
import org.opensources.courses.core.sync.ImportableLists
import org.opensources.courses.core.sync.RemoteListChoice
import org.opensources.courses.core.sync.SyncCoordinator
import org.opensources.courses.core.sync.SyncFailure
import org.opensources.courses.feature.lists.domain.ShoppingList
import org.opensources.courses.testing.FakeHaConfigRepository
import org.opensources.courses.testing.FakeHaListLinkRepository
import org.opensources.courses.testing.FakeHomeAssistantGateway
import org.opensources.courses.testing.FakeRemoteSyncEngine
import org.opensources.courses.testing.FakeShoppingListRepository
import org.opensources.courses.testing.syncCoordinator

@OptIn(ExperimentalCoroutinesApi::class)
class HaRemoteListImportTest {
    private val config = FakeHaConfigRepository()
    private val gateway = FakeHomeAssistantGateway()
    private val links = FakeHaListLinkRepository()
    private val lists = FakeShoppingListRepository()
    private val engine = FakeRemoteSyncEngine()

    private fun TestScope.listImport(coordinator: SyncCoordinator = syncCoordinator(engine, backgroundScope)) =
        HaRemoteListImport(config, gateway, links, lists, coordinator)

    @Test
    fun `offered only in the mode of lists created by this app, once Home Assistant is set up`() =
        runTest {
            val listImport = listImport()
            assertTrue(listImport.isAvailable.first())

            config.setListMode(HaListMode.ALL_LISTS)
            assertFalse(listImport.isAvailable.first())

            config.setListMode(HaListMode.APP_CREATED_ONLY)
            config.setEnabled(false)
            assertFalse(listImport.isAvailable.first())
        }

    @Test
    fun `only usable, editable lists not linked yet are offered`() =
        runTest {
            gateway.lists["todo.mealie"] = HaTodoList("todo.mealie", "Mealie", supportsDescription = false)
            gateway.lists["todo.linked"] = HaTodoList("todo.linked", "Courses", supportsDescription = true)
            gateway.lists["todo.stopped"] = HaTodoList("todo.stopped", "Arrêtée", supportsDescription = true, isAvailable = false)
            gateway.lists["todo.calendar"] = HaTodoList("todo.calendar", "Lecture seule", supportsDescription = false, isEditable = false)
            lists.lists.value = listOf(ShoppingList("l1", "Courses", isDefault = true, remoteId = "todo.linked", syncStatus = SyncStatus.SYNCED))

            val offered = listImport().importableLists()

            assertEquals(ImportableLists.Loaded(listOf(RemoteListChoice("todo.mealie", "Mealie"))), offered)
        }

    @Test
    fun `a Home Assistant that cannot be read says why, and nothing changes`() =
        runTest {
            gateway.failure = HaErrorKind.UNREACHABLE
            assertEquals(ImportableLists.Failed(SyncFailure.UNREACHABLE), listImport().importableLists())

            gateway.failure = HaErrorKind.UNAUTHORIZED
            assertEquals(ImportableLists.Failed(SyncFailure.UNAUTHORIZED), listImport().importableLists())

            config.storedCredentials = null
            assertEquals(ImportableLists.Failed(SyncFailure.UNAUTHORIZED), listImport().importableLists())
            assertTrue(links.imported.isEmpty())
        }

    @Test
    fun `the chosen list is linked under its name and synchronised at once`() =
        runTest {
            val coordinator = syncCoordinator(engine, backgroundScope).apply { start() }
            advanceTimeBy(DEBOUNCE_ELAPSED_MILLIS)
            val before = engine.synchronizations

            val id = listImport(coordinator).importList(RemoteListChoice("todo.mealie", "Mealie"))
            advanceTimeBy(DEBOUNCE_ELAPSED_MILLIS)

            assertEquals("imported:todo.mealie", id)
            assertEquals(mapOf("todo.mealie" to "Mealie"), links.imported)
            assertEquals(before + 1, engine.synchronizations)
        }

    private companion object {
        /** Past the grouping delay of the synchronisation requests (1,5 s). */
        const val DEBOUNCE_ELAPSED_MILLIS = 2_000L
    }
}
