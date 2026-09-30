package org.opensources.courses.feature.homeassistant.data.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.courses.core.sync.SyncOperationType
import org.opensources.courses.core.sync.SyncOutcome
import org.opensources.courses.core.sync.SyncQueue
import org.opensources.courses.feature.homeassistant.domain.HaListMode
import org.opensources.courses.feature.homeassistant.domain.HaTodoList
import org.opensources.courses.testing.FakeAppLanguageRepository
import org.opensources.courses.testing.FakeCatalogRepository
import org.opensources.courses.testing.FakeHaConfigRepository
import org.opensources.courses.testing.FakeHaEntityRegistry
import org.opensources.courses.testing.FakeHaLiveUpdates
import org.opensources.courses.testing.FakeHomeAssistantGateway
import org.opensources.courses.testing.FakeSyncLocalStore
import org.opensources.courses.testing.FakeSyncOperationDao
import org.opensources.courses.testing.fixedClock

/** The user keeps using the app while a synchronisation waits for Home Assistant. */
class HomeAssistantConcurrentChangesTest {
    private val dao = FakeSyncOperationDao()
    private val queue = SyncQueue(dao, fixedClock())
    private val store = FakeSyncLocalStore(queue)
    private val gateway = FakeHomeAssistantGateway().apply { lists[ENTITY] = HaTodoList(ENTITY, "Courses", supportsDescription = true) }
    private val config = FakeHaConfigRepository()
    private val engine =
        HomeAssistantSyncEngine(
            config,
            gateway,
            store,
            queue,
            FakeCatalogRepository(),
            FakeHaLiveUpdates(),
            FakeAppLanguageRepository(),
            FakeHaEntityRegistry(),
        )

    @Test
    fun `an item deleted while Home Assistant creates it does not come back`() =
        runTest {
            store.lists[LIST] = SyncListRef(LIST, "Courses", ENTITY)
            store.items["a"] = SyncItemRef("a", LIST, "Lait", 1.0, null, false, null, false)
            queue.enqueue(SyncOperationType.CREATE_ITEM, LIST, "a")
            // As the app deletes an item that never reached Home Assistant: the row goes, its operation stays.
            gateway.duringAddition = { store.items.remove("a") }

            assertEquals(SyncOutcome.Success, engine.synchronize())
            assertTrue(store.items.values.all { it.isDeleted })

            gateway.duringAddition = null
            assertEquals(SyncOutcome.Success, engine.synchronize())

            assertTrue(gateway.remote(ENTITY).isEmpty())
            assertTrue(store.items.isEmpty())
            assertTrue(dao.all.isEmpty())
        }

    @Test
    fun `a list deleted while Home Assistant creates it is deleted there too`() =
        runTest {
            store.lists[LIST] = SyncListRef(LIST, "BBQ", remoteId = null)
            queue.enqueue(SyncOperationType.CREATE_LIST, LIST)
            // As the app deletes a list never created in Home Assistant: nothing of it stays queued.
            gateway.duringListCreation = {
                store.lists.remove(LIST)
                queue.clearList(LIST)
            }

            assertEquals(SyncOutcome.Success, engine.synchronize())
            gateway.duringListCreation = null
            // The "all lists" mode must not import it meanwhile.
            config.setListMode(HaListMode.ALL_LISTS)
            assertEquals(SyncOutcome.Success, engine.synchronize())

            assertEquals(listOf("entry-$CREATED"), gateway.deletedEntries)
            assertTrue(CREATED !in gateway.lists)
            assertTrue(store.lists.values.none { it.remoteId == CREATED })
            assertTrue(dao.all.isEmpty())
        }

    private companion object {
        const val LIST = "list-1"
        const val ENTITY = "todo.courses"
        const val CREATED = "todo.bbq"
    }
}
