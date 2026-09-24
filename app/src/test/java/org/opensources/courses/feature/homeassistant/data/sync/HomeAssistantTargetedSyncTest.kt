package org.opensources.courses.feature.homeassistant.data.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.courses.core.model.SyncStatus
import org.opensources.courses.core.sync.SyncFailure
import org.opensources.courses.core.sync.SyncOperationType
import org.opensources.courses.core.sync.SyncOutcome
import org.opensources.courses.core.sync.SyncQueue
import org.opensources.courses.core.sync.SyncRequest
import org.opensources.courses.feature.homeassistant.domain.HaErrorKind
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

/**
 * Synchronisations that do not need everything: the lists of Home Assistant are read again only when
 * they may have changed, and a change announced live looks only at its list.
 */
class HomeAssistantTargetedSyncTest {
    private val queue = SyncQueue(FakeSyncOperationDao(), fixedClock())
    private val store = FakeSyncLocalStore(queue).apply { lists[LIST] = SyncListRef(LIST, "Courses", ENTITY) }
    private val gateway = FakeHomeAssistantGateway().apply { addList(ENTITY, "Courses") }
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

    private fun FakeHomeAssistantGateway.addList(
        entityId: String,
        name: String,
    ) {
        lists[entityId] = HaTodoList(entityId, name, supportsDescription = true)
    }

    @Test
    fun `a change of items reuses the lists read by the previous synchronisation`() =
        runTest {
            engine.synchronize(SyncRequest.Full)
            gateway.addRemote(ENTITY, "Lait")

            assertEquals(SyncOutcome.Success, engine.synchronize(SyncRequest.remoteItems(setOf(ENTITY))))
            assertEquals(SyncOutcome.Success, engine.synchronize(SyncRequest.LocalChanges))

            assertEquals(1, gateway.listReads)
            assertEquals(listOf("Lait"), store.items.values.map { it.name })
        }

    @Test
    fun `a full synchronisation reads the lists again`() =
        runTest {
            engine.synchronize(SyncRequest.Full)
            engine.synchronize(SyncRequest.Full)

            assertEquals(2, gateway.listReads)
        }

    @Test
    fun `lists are read again after a failed synchronisation`() =
        runTest {
            engine.synchronize(SyncRequest.Full)
            gateway.failure = HaErrorKind.UNREACHABLE
            assertEquals(SyncOutcome.Failure(SyncFailure.UNREACHABLE), engine.synchronize(SyncRequest.LocalChanges))
            gateway.failure = null

            engine.synchronize(SyncRequest.LocalChanges)

            assertEquals(2, gateway.listReads)
        }

    @Test
    fun `lists are read again when a list is to be created or deleted`() =
        runTest {
            engine.synchronize(SyncRequest.Full)
            store.lists[OTHER_LIST] = SyncListRef(OTHER_LIST, "Bricolage", remoteId = null)
            queue.enqueue(SyncOperationType.CREATE_LIST, OTHER_LIST)

            engine.synchronize(SyncRequest.LocalChanges)
            // The list just created is not among the lists read before its creation.
            engine.synchronize(SyncRequest.LocalChanges)

            assertEquals(3, gateway.listReads)
            assertTrue(store.lists.getValue(OTHER_LIST).remoteId != null)
            assertTrue(store.unlinked.isEmpty())
        }

    @Test
    fun `a list linked since the lists were read is synchronised, not unlinked`() =
        runTest {
            engine.synchronize(SyncRequest.Full)
            gateway.addList(OTHER_ENTITY, "Bricolage")
            gateway.addRemote(OTHER_ENTITY, "Vis")
            store.lists[OTHER_LIST] = SyncListRef(OTHER_LIST, "Bricolage", OTHER_ENTITY)

            assertEquals(SyncOutcome.Success, engine.synchronize(SyncRequest.LocalChanges))

            assertEquals(2, gateway.listReads)
            assertTrue(store.unlinked.isEmpty())
            assertEquals(listOf("Vis"), store.items.values.filter { it.listLocalId == OTHER_LIST }.map { it.name })
        }

    @Test
    fun `lists read with another token are not reused`() =
        runTest {
            engine.synchronize(SyncRequest.Full)
            config.config.value = config.config.value.copy(tokenVersion = config.config.value.tokenVersion + 1)

            engine.synchronize(SyncRequest.LocalChanges)

            assertEquals(2, gateway.listReads)
        }

    @Test
    fun `lists reused never import or remove a list`() =
        runTest {
            config.setListMode(HaListMode.ALL_LISTS)
            engine.synchronize(SyncRequest.Full)
            gateway.addList(OTHER_ENTITY, "Bricolage")

            engine.synchronize(SyncRequest.LocalChanges)
            assertTrue(store.lists.values.none { it.remoteId == OTHER_ENTITY })

            engine.synchronize(SyncRequest.Full)
            assertTrue(store.lists.values.any { it.remoteId == OTHER_ENTITY })
        }

    @Test
    fun `a change announced live synchronises only its list`() =
        runTest {
            gateway.addList(OTHER_ENTITY, "Bricolage")
            store.lists[OTHER_LIST] = SyncListRef(OTHER_LIST, "Bricolage", OTHER_ENTITY)
            engine.synchronize(SyncRequest.Full)
            gateway.addRemote(ENTITY, "Lait")
            gateway.addRemote(OTHER_ENTITY, "Vis")
            val readsBefore = gateway.itemReads

            engine.synchronize(SyncRequest.remoteItems(setOf(OTHER_ENTITY)))

            assertEquals(readsBefore + 1, gateway.itemReads)
            assertEquals(listOf("Vis"), store.items.values.map { it.name })
        }

    @Test
    fun `items already known as synchronised are not written again`() =
        runTest {
            val uid = gateway.addRemote(ENTITY, "Lait")
            val otherUid = gateway.addRemote(ENTITY, "Pain")
            store.items["a"] = SyncItemRef("a", LIST, "Lait", 1.0, null, false, uid, false, SyncStatus.SYNCED)
            store.items["b"] = SyncItemRef("b", LIST, "Pain", 1.0, null, false, otherUid, false, SyncStatus.PENDING)

            engine.synchronize(SyncRequest.Full)

            assertEquals(listOf("b"), store.markedSynced)
        }

    @Test
    fun `the remote state of a list is applied in one transaction`() =
        runTest {
            gateway.addRemote(ENTITY, "Lait")
            gateway.addRemote(ENTITY, "Pain")

            engine.synchronize(SyncRequest.Full)

            assertEquals(1, store.transactions)
            assertEquals(2, store.items.size)
        }

    private companion object {
        const val LIST = "list-1"
        const val ENTITY = "todo.courses"
        const val OTHER_LIST = "list-2"
        const val OTHER_ENTITY = "todo.bricolage"
    }
}
