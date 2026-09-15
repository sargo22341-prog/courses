package org.opensources.courses.feature.homeassistant.data.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.courses.core.sync.SyncFailure
import org.opensources.courses.core.sync.SyncOperationType
import org.opensources.courses.core.sync.SyncOutcome
import org.opensources.courses.core.sync.SyncQueue
import org.opensources.courses.feature.homeassistant.domain.HaTodoList
import org.opensources.courses.testing.FakeCatalogRepository
import org.opensources.courses.testing.FakeHaConfigRepository
import org.opensources.courses.testing.FakeHaLiveUpdates
import org.opensources.courses.testing.FakeHomeAssistantGateway
import org.opensources.courses.testing.FakeSyncLocalStore
import org.opensources.courses.testing.FakeSyncOperationDao
import org.opensources.courses.testing.TestNow
import org.opensources.courses.testing.fixedClock
import org.opensources.courses.testing.product

/** Changes made on both sides, possibly while one of them was offline, must all survive. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeAssistantBidirectionalSyncTest {
    private val dao = FakeSyncOperationDao()
    private val queue = SyncQueue(dao, fixedClock())
    private val store = FakeSyncLocalStore(queue).apply { lists[LIST] = SyncListRef(LIST, "Courses", ENTITY) }
    private val gateway = FakeHomeAssistantGateway().apply { lists[ENTITY] = HaTodoList(ENTITY, "Courses", supportsDescription = true) }
    private val catalog = FakeCatalogRepository(listOf(product("Tomates")))
    private val liveUpdates = FakeHaLiveUpdates()
    private val engine = HomeAssistantSyncEngine(FakeHaConfigRepository(), gateway, store, queue, catalog, liveUpdates)

    private fun localItem(
        id: String,
        name: String,
        checked: Boolean = false,
        remoteId: String? = null,
        deleted: Boolean = false,
    ) {
        store.items[id] = SyncItemRef(id, LIST, name, 1.0, null, checked, remoteId, deleted)
    }

    @Test
    fun `items added in Home Assistant are pulled and joined to the food catalog`() =
        runTest {
            gateway.addRemote(ENTITY, "tomates")
            gateway.addRemote(ENTITY, "Sauce maison")

            engine.synchronize()

            val pulled = store.items.values.associate { it.name to store.catalogProductIds[it.localId] }
            assertEquals("id:Tomates", pulled["tomates"])
            assertEquals("custom:sauce maison", pulled["Sauce maison"])
        }

    @Test
    fun `checking offline keeps a rename made meanwhile in Home Assistant`() =
        runTest {
            val uid = gateway.addRemote(ENTITY, "Lait entier")
            localItem("a", "Lait", checked = true, remoteId = uid)
            queue.enqueue(SyncOperationType.CHECK_ITEM, LIST, "a", remoteItemId = uid)

            engine.synchronize()

            val remote = gateway.remote(ENTITY).single()
            assertEquals("Lait entier", remote.summary)
            assertTrue(remote.completed)
            assertEquals("Lait entier", store.items.getValue("a").name)
        }

    @Test
    fun `item deleted offline but modified in Home Assistant is kept`() =
        runTest {
            val uid = gateway.addRemote(ENTITY, "Pain", completed = true)
            localItem("a", "Pain", remoteId = uid, deleted = true)
            queue.enqueue(SyncOperationType.DELETE_ITEM, LIST, "a", remoteItemId = uid)

            engine.synchronize()

            assertEquals(1, gateway.remote(ENTITY).size)
            val kept = store.items.getValue("a")
            assertFalse(kept.isDeleted)
            assertTrue(kept.isChecked)
            assertTrue(dao.all.isEmpty())
        }

    @Test
    fun `unchecking offline loses against a later check in Home Assistant`() =
        runTest {
            val uid = gateway.addRemote(ENTITY, "Beurre", completed = true, completedAt = TestNow.toEpochMilli() + 60_000)
            localItem("a", "Beurre", checked = false, remoteId = uid)
            queue.enqueue(SyncOperationType.UNCHECK_ITEM, LIST, "a", remoteItemId = uid)

            engine.synchronize()

            assertTrue(gateway.remote(ENTITY).single().completed)
            assertTrue(store.items.getValue("a").isChecked)
        }

    @Test
    fun `unchecking offline wins against an older check in Home Assistant`() =
        runTest {
            val uid = gateway.addRemote(ENTITY, "Beurre", completed = true, completedAt = TestNow.toEpochMilli() - 60_000)
            localItem("a", "Beurre", checked = false, remoteId = uid)
            queue.enqueue(SyncOperationType.UNCHECK_ITEM, LIST, "a", remoteItemId = uid)

            engine.synchronize()

            assertFalse(gateway.remote(ENTITY).single().completed)
            assertFalse(store.items.getValue("a").isChecked)
        }

    @Test
    fun `unavailable Home Assistant list is reported and keeps pending changes`() =
        runTest {
            gateway.lists[ENTITY] = gateway.lists.getValue(ENTITY).copy(isAvailable = false)
            localItem("a", "Lait")
            queue.enqueue(SyncOperationType.CREATE_ITEM, LIST, "a")

            assertEquals(SyncOutcome.Failure(SyncFailure.LIST_UNAVAILABLE), engine.synchronize())

            assertEquals(1, dao.all.size)
            assertTrue(store.unlinked.isEmpty())
        }

    @Test
    fun `live updates follow the linked lists`() =
        runTest {
            var received = 0
            backgroundScope.launch { engine.remoteChanges.collect { received++ } }
            runCurrent()

            liveUpdates.changes.emit(Unit)
            runCurrent()

            assertEquals(setOf(ENTITY), liveUpdates.observedEntityIds)
            assertEquals(1, received)
        }

    private companion object {
        const val LIST = "list-1"
        const val ENTITY = "todo.courses"
    }
}
