package org.opensources.courses.feature.homeassistant.data.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.courses.core.sync.SyncFailure
import org.opensources.courses.core.sync.SyncOperationType
import org.opensources.courses.core.sync.SyncOutcome
import org.opensources.courses.core.sync.SyncQueue
import org.opensources.courses.feature.homeassistant.domain.HaErrorKind
import org.opensources.courses.feature.homeassistant.domain.HaTodoList
import org.opensources.courses.testing.FakeHaConfigRepository
import org.opensources.courses.testing.FakeHomeAssistantGateway
import org.opensources.courses.testing.FakeSyncLocalStore
import org.opensources.courses.testing.FakeSyncOperationDao
import org.opensources.courses.testing.fixedClock

class HomeAssistantSyncEngineTest {
    private val dao = FakeSyncOperationDao()
    private val queue = SyncQueue(dao, fixedClock())
    private val store = FakeSyncLocalStore(queue).apply { lists[LIST] = SyncListRef(LIST, "Courses", ENTITY) }
    private val gateway = FakeHomeAssistantGateway().apply { lists[ENTITY] = HaTodoList(ENTITY, "Courses", supportsDescription = true) }
    private val config = FakeHaConfigRepository()
    private val engine = HomeAssistantSyncEngine(config, gateway, store, queue)

    private fun localItem(
        id: String,
        name: String,
        checked: Boolean = false,
        remoteId: String? = null,
        quantity: Double = 1.0,
        deleted: Boolean = false,
    ) {
        store.items[id] = SyncItemRef(id, LIST, name, quantity, null, checked, remoteId, deleted)
    }

    private suspend fun enqueue(
        type: SyncOperationType,
        itemId: String,
        remoteItemId: String? = null,
    ) = queue.enqueue(type, LIST, itemId, remoteItemId = remoteItemId)

    @Test
    fun `item created offline is sent, gets its uid and leaves the queue`() =
        runTest {
            localItem("a", "Lait", quantity = 2.0)
            enqueue(SyncOperationType.CREATE_ITEM, "a")

            assertEquals(SyncOutcome.Success, engine.synchronize())

            val remote = gateway.remote(ENTITY).single()
            assertEquals("Lait", remote.summary)
            assertEquals("2", remote.description)
            assertEquals(remote.uid, store.items.getValue("a").remoteId)
            assertTrue(dao.all.isEmpty())
        }

    @Test
    fun `item created and checked offline arrives checked`() =
        runTest {
            localItem("a", "Pain", checked = true)
            enqueue(SyncOperationType.CREATE_ITEM, "a")
            enqueue(SyncOperationType.CHECK_ITEM, "a")

            engine.synchronize()

            assertTrue(gateway.remote(ENTITY).single().completed)
            assertTrue(store.items.getValue("a").isChecked)
        }

    @Test
    fun `item checked in Home Assistant is checked locally at next sync`() =
        runTest {
            val uid = gateway.addRemote(ENTITY, "Lait", completed = true)
            localItem("a", "Lait", remoteId = uid)

            engine.synchronize()

            assertTrue(store.items.getValue("a").isChecked)
        }

    @Test
    fun `pending local change is pushed instead of being overwritten`() =
        runTest {
            val uid = gateway.addRemote(ENTITY, "Lait entier")
            localItem("a", "Lait", checked = true, remoteId = uid)
            enqueue(SyncOperationType.CHECK_ITEM, "a", uid)

            engine.synchronize()

            val remote = gateway.remote(ENTITY).single()
            assertEquals("Lait", remote.summary)
            assertTrue(remote.completed)
            assertEquals("Lait", store.items.getValue("a").name)
        }

    @Test
    fun `unreachable Home Assistant keeps every operation and local data`() =
        runTest {
            localItem("a", "Lait")
            enqueue(SyncOperationType.CREATE_ITEM, "a")
            gateway.failure = HaErrorKind.UNREACHABLE

            assertEquals(SyncOutcome.Failure(SyncFailure.UNREACHABLE), engine.synchronize())

            assertEquals(1, dao.all.size)
            assertEquals("Lait", store.items.getValue("a").name)

            gateway.failure = null
            assertEquals(SyncOutcome.Success, engine.synchronize())
            assertEquals("Lait", gateway.remote(ENTITY).single().summary)
            assertTrue(dao.all.isEmpty())
        }

    @Test
    fun `rejected operation stays queued for a later retry`() =
        runTest {
            val uid = gateway.addRemote(ENTITY, "Lait")
            localItem("a", "Lait", checked = true, remoteId = uid)
            enqueue(SyncOperationType.CHECK_ITEM, "a", uid)
            gateway.failingUpdates = HaErrorKind.REJECTED

            assertEquals(SyncOutcome.Failure(SyncFailure.PROTOCOL), engine.synchronize())

            assertEquals(1, dao.all.single().attemptCount)
            assertTrue(store.items.getValue("a").isChecked)
        }

    @Test
    fun `remote deletions and additions are applied to synced items`() =
        runTest {
            localItem("a", "Pain", remoteId = "uid-gone")
            gateway.addRemote(ENTITY, "Beurre", description = "250 g")

            engine.synchronize()

            assertFalse("a" in store.items)
            val pulled = store.items.values.single()
            assertEquals("Beurre", pulled.name)
            assertEquals(250.0, pulled.quantity, 0.0)
            assertEquals("g", pulled.unit)
        }

    @Test
    fun `local deletion removes the remote item and purges the tombstone`() =
        runTest {
            val uid = gateway.addRemote(ENTITY, "Pain")
            localItem("a", "Pain", remoteId = uid, deleted = true)
            enqueue(SyncOperationType.DELETE_ITEM, "a", uid)

            engine.synchronize()

            assertTrue(gateway.remote(ENTITY).isEmpty())
            assertTrue(store.items.isEmpty())
            assertTrue(dao.all.isEmpty())
        }

    @Test
    fun `linking to a list with the same items does not duplicate them`() =
        runTest {
            val uid = gateway.addRemote(ENTITY, "Pain")
            localItem("a", "pain")
            enqueue(SyncOperationType.CREATE_ITEM, "a")

            engine.synchronize()

            assertEquals(1, gateway.remote(ENTITY).size)
            assertEquals(uid, store.items.getValue("a").remoteId)
        }

    @Test
    fun `list created by the app is created remotely before its items`() =
        runTest {
            store.lists[LIST] = SyncListRef(LIST, "BBQ", remoteId = null)
            queue.enqueue(SyncOperationType.CREATE_LIST, LIST)
            localItem("a", "Merguez")
            enqueue(SyncOperationType.CREATE_ITEM, "a")

            assertEquals(SyncOutcome.Success, engine.synchronize())

            assertEquals("Merguez", gateway.remote("todo.bbq").single().summary)
            assertTrue("todo.bbq" in store.tracked)
            assertTrue(dao.all.isEmpty())
        }

    @Test
    fun `list deleted in Home Assistant is unlinked but kept locally`() =
        runTest {
            gateway.lists.clear()
            localItem("a", "Lait")

            engine.synchronize()

            assertTrue(LIST in store.unlinked)
            assertTrue("a" in store.items)
        }

    @Test
    fun `deleting an app-created list removes its config entry`() =
        runTest {
            queue.enqueue(SyncOperationType.DELETE_LIST, "gone", remoteListId = "todo.bbq", remoteEntryId = "entry-1")

            engine.synchronize()

            assertEquals(listOf("entry-1"), gateway.deletedEntries)
            assertTrue(dao.all.isEmpty())
        }

    @Test
    fun `nothing happens without credentials`() =
        runTest {
            config.storedCredentials = null
            localItem("a", "Lait")
            enqueue(SyncOperationType.CREATE_ITEM, "a")

            assertEquals(SyncOutcome.Skipped, engine.synchronize())
            assertEquals(1, dao.all.size)
        }

    private companion object {
        const val LIST = "list-1"
        const val ENTITY = "todo.courses"
    }
}
