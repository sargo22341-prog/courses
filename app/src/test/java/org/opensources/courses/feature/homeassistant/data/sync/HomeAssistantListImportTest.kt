package org.opensources.courses.feature.homeassistant.data.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.courses.core.sync.SyncFailure
import org.opensources.courses.core.sync.SyncOperationType
import org.opensources.courses.core.sync.SyncOutcome
import org.opensources.courses.core.sync.SyncQueue
import org.opensources.courses.feature.homeassistant.domain.HaErrorKind
import org.opensources.courses.feature.homeassistant.domain.HaListMode
import org.opensources.courses.feature.homeassistant.domain.HaTodoList
import org.opensources.courses.testing.FakeCatalogRepository
import org.opensources.courses.testing.FakeHaConfigRepository
import org.opensources.courses.testing.FakeHaLiveUpdates
import org.opensources.courses.testing.FakeHomeAssistantGateway
import org.opensources.courses.testing.FakeSyncLocalStore
import org.opensources.courses.testing.FakeSyncOperationDao
import org.opensources.courses.testing.fixedClock

/** The two list modes: which Home Assistant lists reach the app, and what leaves it. */
class HomeAssistantListImportTest {
    private val queue = SyncQueue(FakeSyncOperationDao(), fixedClock())
    private val store = FakeSyncLocalStore(queue).apply { lists[LIST] = SyncListRef(LIST, "Courses", ENTITY) }
    private val gateway = FakeHomeAssistantGateway().apply { addList(ENTITY, "Courses") }
    private val config = FakeHaConfigRepository()
    private val engine = HomeAssistantSyncEngine(config, gateway, store, queue, FakeCatalogRepository(), FakeHaLiveUpdates())

    private fun FakeHomeAssistantGateway.addList(
        entityId: String,
        name: String,
        editable: Boolean = true,
        available: Boolean = true,
    ) {
        lists[entityId] = HaTodoList(entityId, name, supportsDescription = true, isAvailable = available, isEditable = editable)
    }

    private suspend fun allListsMode() = config.setListMode(HaListMode.ALL_LISTS)

    private fun importedNames(): Map<String?, String> = store.lists.values.filter { it.importedFromRemote }.associate { it.remoteId to it.name }

    private fun imported(
        id: String,
        name: String,
        entityId: String,
        remoteName: String = name,
    ) {
        store.lists[id] = SyncListRef(id, name, entityId, importedFromRemote = true, remoteName = remoteName)
    }

    @Test
    fun `all lists mode imports every editable list under a name free in the app, with its items`() =
        runTest {
            allListsMode()
            gateway.addList("todo.courses_maison", "Courses")
            gateway.addList("todo.bricolage", "Bricolage")
            gateway.addRemote("todo.bricolage", "Vis")
            gateway.addList("todo.anniversaires", "Anniversaires", editable = false)
            gateway.addList("todo.jardin", "Jardin", available = false)

            assertEquals(SyncOutcome.Success, engine.synchronize())

            assertEquals(mapOf("todo.courses_maison" to "Courses 2", "todo.bricolage" to "Bricolage"), importedNames())
            val bricolage = store.lists.values.single { it.remoteId == "todo.bricolage" }
            assertEquals(listOf("Vis"), store.items.values.filter { it.listLocalId == bricolage.localId }.map { it.name })
        }

    @Test
    fun `a list created later in Home Assistant arrives at the next synchronisation, once`() =
        runTest {
            allListsMode()
            engine.synchronize()
            gateway.addList("todo.vacances", "Vacances")

            engine.synchronize()
            engine.synchronize()

            assertEquals(mapOf("todo.vacances" to "Vacances"), importedNames())
        }

    @Test
    fun `lists created by the app mode imports nothing and removes the lists imported before`() =
        runTest {
            imported("old", "Bricolage", "todo.bricolage")
            gateway.addList("todo.bricolage", "Bricolage")
            gateway.addList("todo.vacances", "Vacances")

            assertEquals(SyncOutcome.Success, engine.synchronize())

            assertEquals(setOf(LIST), store.lists.keys)
            assertTrue("old" in store.removedLists)
        }

    @Test
    fun `a list removed from the app stays in Home Assistant and is not imported again`() =
        runTest {
            allListsMode()
            gateway.addList("todo.bricolage", "Bricolage")
            queue.enqueue(SyncOperationType.DELETE_LIST, "gone", remoteListId = "todo.bricolage")

            engine.synchronize()
            engine.synchronize()

            assertTrue("todo.bricolage" in store.ignored)
            assertTrue("todo.bricolage" in gateway.lists)
            assertTrue(gateway.deletedEntries.isEmpty())
            assertTrue(importedNames().isEmpty())
        }

    @Test
    fun `ignored lists and lists whose deletion is still queued are not imported`() =
        runTest {
            allListsMode()
            gateway.addList("todo.bricolage", "Bricolage")
            gateway.addList("todo.menage", "Ménage")
            store.ignored += "todo.bricolage"
            queue.enqueue(SyncOperationType.DELETE_LIST, "gone", remoteListId = "todo.menage", remoteEntryId = "entry-todo.menage")
            gateway.failingDeletions = HaErrorKind.REJECTED

            assertEquals(SyncOutcome.Failure(SyncFailure.PROTOCOL), engine.synchronize())

            assertTrue(importedNames().isEmpty())
        }

    @Test
    fun `in all lists mode a list deleted in Home Assistant leaves the app`() =
        runTest {
            allListsMode()
            gateway.lists.remove(ENTITY)
            store.items["a"] = SyncItemRef("a", LIST, "Lait", 1.0, null, false, "uid-1", false)

            engine.synchronize()

            assertTrue(LIST in store.removedLists)
            assertTrue(store.items.isEmpty())
        }

    @Test
    fun `a list deleted in Home Assistant with changes not sent yet is only unlinked`() =
        runTest {
            allListsMode()
            gateway.lists.remove(ENTITY)
            store.items["a"] = SyncItemRef("a", LIST, "Lait", 1.0, null, false, null, false)
            queue.enqueue(SyncOperationType.CREATE_ITEM, LIST, "a")

            engine.synchronize()

            assertTrue(LIST in store.unlinked)
            assertTrue("a" in store.items)
        }

    @Test
    fun `a rename in Home Assistant renames imported lists, unless renamed in the app since`() =
        runTest {
            allListsMode()
            gateway.addList(ENTITY, "Épicerie")
            imported("bricolage", "Bricolage", "todo.bricolage")
            gateway.addList("todo.bricolage", "Courses")
            imported("jardin", "Potager", "todo.jardin", remoteName = "Jardin")
            gateway.addList("todo.jardin", "Jardin")

            engine.synchronize()

            assertEquals("Courses", store.lists.getValue(LIST).name)
            assertEquals("Courses 2", store.lists.getValue("bricolage").name)
            assertEquals("Potager", store.lists.getValue("jardin").name)
        }

    private companion object {
        const val LIST = "list-1"
        const val ENTITY = "todo.courses"
    }
}
