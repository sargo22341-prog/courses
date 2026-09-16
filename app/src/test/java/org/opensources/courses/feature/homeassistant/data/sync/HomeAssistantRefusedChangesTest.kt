package org.opensources.courses.feature.homeassistant.data.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.courses.core.sync.SyncFailure
import org.opensources.courses.core.sync.SyncOperationType
import org.opensources.courses.core.sync.SyncOutcome
import org.opensources.courses.core.sync.SyncQueue
import org.opensources.courses.feature.homeassistant.domain.HaErrorKind
import org.opensources.courses.feature.homeassistant.domain.HaTodoList
import org.opensources.courses.testing.FakeAppLanguageRepository
import org.opensources.courses.testing.FakeCatalogRepository
import org.opensources.courses.testing.FakeHaConfigRepository
import org.opensources.courses.testing.FakeHaLiveUpdates
import org.opensources.courses.testing.FakeHomeAssistantGateway
import org.opensources.courses.testing.FakeSyncLocalStore
import org.opensources.courses.testing.FakeSyncOperationDao
import org.opensources.courses.testing.fixedClock

/** Home Assistant refusing changes: retried, then given up without being retried forever. */
class HomeAssistantRefusedChangesTest {
    private val dao = FakeSyncOperationDao()
    private val queue = SyncQueue(dao, fixedClock())
    private val store = FakeSyncLocalStore(queue).apply { lists[LIST] = SyncListRef(LIST, "Courses", ENTITY) }
    private val gateway = FakeHomeAssistantGateway().apply { lists[ENTITY] = HaTodoList(ENTITY, "Courses", supportsDescription = true) }
    private val engine =
        HomeAssistantSyncEngine(FakeHaConfigRepository(), gateway, store, queue, FakeCatalogRepository(), FakeHaLiveUpdates(), FakeAppLanguageRepository())

    private fun localItem(
        id: String,
        name: String,
        checked: Boolean = false,
        remoteId: String? = null,
        deleted: Boolean = false,
        listId: String = LIST,
    ) {
        store.items[id] = SyncItemRef(id, listId, name, 1.0, null, checked, remoteId, deleted)
    }

    private suspend fun enqueue(
        type: SyncOperationType,
        itemId: String,
        remoteItemId: String? = null,
        listId: String = LIST,
    ) = queue.enqueue(type, listId, itemId, remoteItemId = remoteItemId)

    /** Every attempt but the last one keeps the change queued. */
    private suspend fun refuseUntilLastAttempt() {
        repeat(SyncQueue.MAX_ATTEMPTS - 1) { attempt ->
            assertEquals(SyncOutcome.Failure(SyncFailure.PROTOCOL), engine.synchronize())
            assertEquals(attempt + 1, dao.all.maxOf { it.attemptCount })
        }
    }

    @Test
    fun `a change refused every time is given up once, and the item takes the Home Assistant state`() =
        runTest {
            val uid = gateway.addRemote(ENTITY, "Lait")
            localItem("a", "Lait", checked = true, remoteId = uid)
            enqueue(SyncOperationType.CHECK_ITEM, "a", uid)
            gateway.failingUpdates = HaErrorKind.REJECTED

            refuseUntilLastAttempt()
            assertEquals(SyncOutcome.Failure(SyncFailure.REJECTED), engine.synchronize())

            assertTrue(dao.all.isEmpty())
            assertFalse(store.items.getValue("a").isChecked)
            // Reported once: nothing is left to refuse.
            assertEquals(SyncOutcome.Success, engine.synchronize())
        }

    @Test
    fun `a deletion refused every time is given up and the item comes back`() =
        runTest {
            val uid = gateway.addRemote(ENTITY, "Pain")
            localItem("a", "Pain", remoteId = uid, deleted = true)
            enqueue(SyncOperationType.DELETE_ITEM, "a", uid)
            gateway.failingRemovals = HaErrorKind.REJECTED

            refuseUntilLastAttempt()
            assertEquals(SyncOutcome.Failure(SyncFailure.REJECTED), engine.synchronize())

            assertFalse(store.items.getValue("a").isDeleted)
            assertEquals("Pain", gateway.remote(ENTITY).single().summary)
            assertTrue(dao.all.isEmpty())
        }

    @Test
    fun `an item Home Assistant never accepts stays on this phone only`() =
        runTest {
            localItem("a", "Lait")
            enqueue(SyncOperationType.CREATE_ITEM, "a")
            gateway.failingAdditions = HaErrorKind.REJECTED

            refuseUntilLastAttempt()
            assertEquals(SyncOutcome.Failure(SyncFailure.REJECTED), engine.synchronize())

            assertEquals("Lait", store.items.getValue("a").name)
            assertNull(store.items.getValue("a").remoteId)
            assertTrue(gateway.remote(ENTITY).isEmpty())
            assertTrue(dao.all.isEmpty())
        }

    @Test
    fun `an unreachable server never counts as a refusal`() =
        runTest {
            localItem("a", "Lait")
            enqueue(SyncOperationType.CREATE_ITEM, "a")
            gateway.failure = HaErrorKind.UNREACHABLE

            repeat(SyncQueue.MAX_ATTEMPTS + 1) { engine.synchronize() }

            assertEquals(0, dao.all.single().attemptCount)
        }

    @Test
    fun `a refused token stops the synchronisation before a list deletion counts as refused`() =
        runTest {
            queue.enqueue(SyncOperationType.DELETE_LIST, "gone", remoteListId = "todo.bbq", remoteEntryId = "entry-todo.bbq")
            gateway.failure = HaErrorKind.UNAUTHORIZED

            assertEquals(SyncOutcome.Failure(SyncFailure.UNAUTHORIZED), engine.synchronize())

            assertEquals(0, dao.all.single().attemptCount)
        }

    @Test
    fun `an item stored under another text is replaced by the Home Assistant copy, never sent twice`() =
        runTest {
            gateway.storedSummary = { "$it (bio)" }
            localItem("a", "Lait")
            enqueue(SyncOperationType.CREATE_ITEM, "a")

            engine.synchronize()
            engine.synchronize()

            assertEquals(listOf("Lait (bio)"), gateway.remote(ENTITY).map { it.summary })
            assertEquals(listOf("Lait (bio)"), store.items.values.map { it.name })
            assertTrue(dao.all.isEmpty())
        }

    @Test
    fun `a refused state of a new item does not stop the other new items`() =
        runTest {
            for (id in listOf("a", "b", "c")) {
                localItem(id, id.uppercase(), checked = id != "c")
                enqueue(SyncOperationType.CREATE_ITEM, id)
            }
            // "a" is created first, so it gets the first uid.
            gateway.refusedOnceUids += "uid1"

            assertEquals(SyncOutcome.Failure(SyncFailure.PROTOCOL), engine.synchronize())

            assertTrue(listOf("a", "b", "c").all { store.items.getValue(it).remoteId != null })
            assertEquals(listOf("a"), dao.all.map { it.itemLocalId }.distinct())
            assertEquals(1, dao.all.single().attemptCount)

            assertEquals(SyncOutcome.Success, engine.synchronize())

            assertEquals(listOf(true, true, false), gateway.remote(ENTITY).map { it.completed })
            assertTrue(dao.all.isEmpty())
        }

    @Test
    fun `a list Home Assistant never creates stays on this phone`() =
        runTest {
            store.lists[LIST] = SyncListRef(LIST, "BBQ", remoteId = null)
            queue.enqueue(SyncOperationType.CREATE_LIST, LIST)
            gateway.failingListCreations = HaErrorKind.REJECTED

            refuseUntilLastAttempt()
            assertEquals(SyncOutcome.Failure(SyncFailure.REJECTED), engine.synchronize())

            assertTrue(LIST in store.unlinked)
            assertTrue(dao.all.isEmpty())
        }

    @Test
    fun `a list deletion refused every time is given up and the list is not imported again`() =
        runTest {
            queue.enqueue(SyncOperationType.DELETE_LIST, "gone", remoteListId = "todo.bbq", remoteEntryId = "entry-todo.bbq")
            gateway.failingDeletions = HaErrorKind.REJECTED

            refuseUntilLastAttempt()
            assertEquals(SyncOutcome.Failure(SyncFailure.REJECTED), engine.synchronize())

            assertTrue("todo.bbq" in store.ignored)
            assertTrue(dao.all.isEmpty())
        }

    @Test
    fun `the queue is read once per synchronisation, not once per item`() =
        runTest {
            val lists = listOf(LIST, "list-2", "list-3")
            lists.forEachIndexed { index, listId ->
                val entityId = "todo.list$index"
                gateway.lists[entityId] = HaTodoList(entityId, "Liste $index", supportsDescription = true)
                store.lists[listId] = SyncListRef(listId, "Liste $index", entityId)
                repeat(7) { itemIndex ->
                    val id = "$listId-$itemIndex"
                    localItem(id, "Article $itemIndex", listId = listId)
                    enqueue(SyncOperationType.CREATE_ITEM, id, listId = listId)
                }
            }
            gateway.lists.remove(ENTITY)

            assertEquals(SyncOutcome.Success, engine.synchronize())

            // The whole queue once, then the items still pending once per list.
            assertEquals(1 + lists.size, dao.reads)
            assertTrue(dao.all.isEmpty())
        }

    @Test
    fun `a list without pending change is read once`() =
        runTest {
            gateway.addRemote(ENTITY, "Lait")

            engine.synchronize()

            assertEquals(1, gateway.itemReads)
        }

    private companion object {
        const val LIST = "list-1"
        const val ENTITY = "todo.courses"
    }
}
