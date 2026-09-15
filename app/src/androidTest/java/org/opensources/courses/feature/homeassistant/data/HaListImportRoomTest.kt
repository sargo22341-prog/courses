package org.opensources.courses.feature.homeassistant.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.courses.core.model.SyncStatus
import org.opensources.courses.core.sync.SyncOperationType
import org.opensources.courses.feature.shopping.domain.NewShoppingItem
import org.opensources.courses.testing.TestRepositories

/** Lists imported by the "all lists" mode, on a real Room database. */
@RunWith(AndroidJUnit4::class)
class HaListImportRoomTest {
    private val repositories = TestRepositories.inMemory(InstrumentationRegistry.getInstrumentation().targetContext)
    private val store = repositories.syncStore
    private val listDao = repositories.database.shoppingListDao()

    @After
    fun close() {
        repositories.database.close()
    }

    private suspend fun names() = listDao.getAll().map { it.name }

    private suspend fun byRemote(entityId: String) = listDao.getAll().single { it.remoteId == entityId }

    @Test
    fun importedListTakesAFreeNameOnceAndIgnoredListsAreSkipped() =
        runTest {
            repositories.lists.createList("Courses")

            store.importList("todo.courses", "Courses")
            store.importList("todo.courses", "Courses")
            store.ignoreList("todo.bricolage")
            store.importList("todo.bricolage", "Bricolage")

            assertEquals(listOf("Courses", "Courses 2"), names())
            val imported = byRemote("todo.courses")
            assertTrue(imported.importedFromRemote)
            assertEquals(SyncStatus.SYNCED, imported.syncStatus)
            assertFalse(imported.isDefault)
        }

    @Test
    fun remoteRenameIsFollowedUntilTheListIsRenamedInTheApp() =
        runTest {
            repositories.lists.createList("Courses")
            store.importList("todo.bricolage", "Bricolage")
            val id = byRemote("todo.bricolage").localId

            store.applyRemoteListName(id, "Courses")
            assertEquals("Courses 2", listDao.getById(id)?.name)

            repositories.lists.renameList(id, "Atelier")
            store.applyRemoteListName(id, "Courses")
            assertEquals("Atelier", listDao.getById(id)?.name)
        }

    @Test
    fun deletingOrUnlinkingAListCreatedElsewhereStopsItsImport() =
        runTest {
            repositories.lists.createList("Courses")
            store.importList("todo.bricolage", "Bricolage")
            store.importList("todo.menage", "Ménage")

            assertTrue(repositories.lists.deleteList(byRemote("todo.bricolage").localId))
            val deletion = repositories.queue.pending().single()
            assertEquals(SyncOperationType.DELETE_LIST, deletion.type)
            assertEquals("todo.bricolage", deletion.remoteListId)
            assertNull(deletion.remoteEntryId)

            val menage = byRemote("todo.menage").localId
            repositories.links.unlink(menage)
            assertEquals(setOf("todo.menage"), store.ignoredEntityIds())
            assertFalse(listDao.getById(menage)!!.importedFromRemote)

            repositories.links.linkToExisting(menage, "todo.menage")
            assertTrue(store.ignoredEntityIds().isEmpty())
        }

    @Test
    fun leavingAllListsModeRemovesImportedListsButKeepsTheLastOneUnlinked() =
        runTest {
            store.importList("todo.courses", "Courses")
            store.importList("todo.bricolage", "Bricolage")

            repositories.links.removeImportedLists()

            val remaining = listDao.getAll().single()
            assertEquals("Courses", remaining.name)
            assertTrue(remaining.isDefault)
            assertNull(remaining.remoteId)
            assertFalse(remaining.importedFromRemote)
        }

    @Test
    fun leavingAllListsModeKeepsTheUserListsAndMovesTheDefaultToThem() =
        runTest {
            store.importList("todo.courses", "Courses")
            val mine = repositories.lists.createList("Maison")

            repositories.links.removeImportedLists()

            assertEquals(listOf(mine.id), listDao.getAll().map { it.localId })
            assertEquals(mine.id, repositories.lists.observeDefaultList().first()?.id)
        }

    @Test
    fun listDeletedInHomeAssistantIsRemovedUnlessItHoldsChangesNotSentYet() =
        runTest {
            repositories.lists.createList("Courses")
            store.importList("todo.bricolage", "Bricolage")
            store.importList("todo.menage", "Ménage")
            val menage = byRemote("todo.menage").localId
            repositories.items.addItem(NewShoppingItem(menage, "Éponge"))

            store.removeRemotelyDeletedList(byRemote("todo.bricolage").localId)
            store.removeRemotelyDeletedList(menage)

            assertEquals(listOf("Courses", "Ménage"), names())
            assertNull(listDao.getById(menage)?.remoteId)
            assertEquals(listOf("Éponge"), repositories.items.getItems(menage).map { it.name })
        }

    @Test
    fun linkingAListToAnImportedListReplacesTheImportedCopy() =
        runTest {
            store.importList("todo.courses", "Courses")
            val mine = repositories.lists.createList("Maison")

            repositories.links.linkToExisting(mine.id, "todo.courses")

            val remaining = listDao.getAll().single()
            assertEquals(mine.id, remaining.localId)
            assertEquals("todo.courses", remaining.remoteId)
            assertTrue(remaining.isDefault)
        }
}
