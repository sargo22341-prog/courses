package org.opensources.courses.feature.homeassistant.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.courses.core.model.SyncStatus
import org.opensources.courses.feature.homeassistant.data.local.HaTrackedListEntity
import org.opensources.courses.testing.TestRepositories

/**
 * A Home Assistant list imported by hand from the new list dialog, and what the synchronisation keeps
 * about Home Assistant lists and items, on a real Room database.
 */
@RunWith(AndroidJUnit4::class)
class HaChosenListImportRoomTest {
    private val repositories = TestRepositories.inMemory(InstrumentationRegistry.getInstrumentation().targetContext)
    private val links = repositories.links
    private val store = repositories.syncStore
    private val listDao = repositories.database.shoppingListDao()

    @After
    fun close() {
        repositories.database.close()
    }

    @Test
    fun anImportedListIsLinkedUnderAFreeNameAndStaysWhenTheAllListsModeIsLeft() =
        runTest {
            repositories.lists.createList("Mealie")

            val id = links.importList("todo.mealie", "Mealie")
            assertEquals(id, links.importList("todo.mealie", "Mealie"))
            links.removeImportedLists()

            val imported = listDao.getById(id)!!
            assertEquals("Mealie 2", imported.name)
            assertEquals("todo.mealie", imported.remoteId)
            assertEquals(SyncStatus.SYNCED, imported.syncStatus)
            assertFalse(imported.importedFromRemote)
            assertNull(imported.remoteName)
            assertFalse(imported.isDefault)
            assertEquals(2, listDao.getAll().size)
        }

    @Test
    fun importingAListCancelsItsPendingDeletionAndItsIgnoring() =
        runTest {
            repositories.lists.createList("Courses")
            store.importList("todo.bricolage", "Bricolage")
            repositories.lists.deleteList(listDao.getAll().single { it.remoteId == "todo.bricolage" }.localId)
            store.ignoreList("todo.bricolage")

            links.importList("todo.bricolage", "Bricolage")

            assertTrue(repositories.queue.pending().isEmpty())
            assertTrue(store.ignoredEntityIds().isEmpty())
        }

    @Test
    fun aListCreatedByThisAppKeepsItsConfigEntry() =
        runTest {
            repositories.database.haTrackedListDao().upsert(HaTrackedListEntity("todo.courses", "entry-1"))

            val imported = listDao.getById(links.importList("todo.courses", "Courses"))!!

            assertEquals("entry-1", imported.remoteEntryId)
            assertTrue(imported.createdByApp)
            assertTrue(imported.isDefault)
        }

    @Test
    fun listIntegrationsAreKeptUntilHomeAssistantIsForgotten() =
        runTest {
            store.saveIntegrations(mapOf("todo.mealie" to "mealie", "todo.legacy" to null))

            assertEquals(
                mapOf("todo.mealie" to "mealie", "todo.legacy" to null),
                store.knownIntegrations(listOf("todo.mealie", "todo.legacy", "todo.new")),
            )

            links.unlinkAll()
            assertTrue(store.knownIntegrations(listOf("todo.mealie", "todo.legacy")).isEmpty())
        }

    @Test
    fun aRemoteRenameTakesTheProductToldOrDropsTheFormerLink() =
        runTest {
            val list = repositories.lists.createList("Mealie")
            store.insertRemoteItem(list.id, "uid-1", "250 grammes Pâtes", 1.0, null, false, "custom:raw")
            store.insertRemoteItem(list.id, "uid-2", "Lait", 1.0, null, false, "seed:lait")
            val (pates, lait) = store.items(list.id).sortedBy { it.remoteId }

            store.applyRemoteItem(pates.localId, "Pâtes", 250.0, "g", false, "seed:pates")
            store.applyRemoteItem(lait.localId, "Lait entier", 1.0, null, true)

            val items = store.items(list.id).associateBy { it.remoteId }
            assertEquals("Pâtes", items.getValue("uid-1").name)
            assertEquals(250.0, items.getValue("uid-1").quantity, 0.0)
            assertEquals("seed:pates", items.getValue("uid-1").catalogProductId)
            assertNull(items.getValue("uid-2").catalogProductId)
        }
}
