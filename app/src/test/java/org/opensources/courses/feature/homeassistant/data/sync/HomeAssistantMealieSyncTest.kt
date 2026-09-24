package org.opensources.courses.feature.homeassistant.data.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.courses.core.sync.SyncOperationType
import org.opensources.courses.core.sync.SyncOutcome
import org.opensources.courses.core.sync.SyncQueue
import org.opensources.courses.feature.catalog.domain.CatalogSource
import org.opensources.courses.feature.homeassistant.domain.HaErrorKind
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
import org.opensources.courses.testing.product

/**
 * A Mealie shopping list, as Home Assistant exposes it: no description, item texts written by Mealie
 * ("250 grammes Pâtes"), and a changed text turns the item into a plain note in Mealie.
 */
class HomeAssistantMealieSyncTest {
    private val queue = SyncQueue(FakeSyncOperationDao(), fixedClock())
    private val store = FakeSyncLocalStore(queue).apply { lists[LIST] = SyncListRef(LIST, "Mealie", MEALIE) }
    private val gateway = FakeHomeAssistantGateway().apply { lists[MEALIE] = HaTodoList(MEALIE, "Mealie", supportsDescription = false) }
    private val registry = FakeHaEntityRegistry(mutableMapOf(MEALIE to "mealie"))
    private val catalog =
        FakeCatalogRepository(
            listOf(
                product("Pâtes", id = "seed:pates"),
                product("Ail", id = "seed:ail"),
                product("Pain", id = "seed:pain"),
                product("Crevettes", id = "off:crevettes", source = CatalogSource.OPEN_FOOD_FACTS),
            ),
        )
    private val engine =
        HomeAssistantSyncEngine(
            FakeHaConfigRepository(),
            gateway,
            store,
            queue,
            catalog,
            FakeHaLiveUpdates(),
            FakeAppLanguageRepository(),
            registry,
        )

    private fun localItem(
        id: String,
        name: String,
        quantity: Double = 1.0,
        unit: String? = null,
        remoteId: String? = null,
        checked: Boolean = false,
        productId: String? = null,
    ) {
        store.items[id] = SyncItemRef(id, LIST, name, quantity, unit, checked, remoteId, isDeleted = false, catalogProductId = productId)
    }

    private fun item(name: String): SyncItemRef = store.items.values.single { it.name == name }

    @Test
    fun `Mealie items are read as article, quantity and unit, filed under the product they name`() =
        runTest {
            gateway.addRemote(MEALIE, "250 grammes Pâtes")
            gateway.addRemote(MEALIE, "1 gousse ail")
            gateway.addRemote(MEALIE, "200 grammes crevettes décortiquées de")
            gateway.addRemote(MEALIE, "graines de sésame ou selon le goût")

            assertEquals(SyncOutcome.Success, engine.synchronize())

            assertEquals(250.0, item("Pâtes").quantity, 0.0)
            assertEquals("g", item("Pâtes").unit)
            assertEquals("seed:pates", item("Pâtes").catalogProductId)
            assertEquals("seed:ail", item("gousse ail").catalogProductId)
            assertEquals("off:crevettes", item("crevettes décortiquées").catalogProductId)
            val sesame = item("graines de sésame ou selon le goût")
            assertEquals(1.0, sesame.quantity, 0.0)
            assertEquals("custom:graines de sesame ou selon le gout", sesame.catalogProductId)
        }

    @Test
    fun `the integration of a list is asked once and kept`() =
        runTest {
            engine.synchronize()
            engine.synchronize()

            assertEquals(listOf(listOf(MEALIE)), registry.questions)
            assertEquals(mapOf(MEALIE to "mealie"), store.integrations)
        }

    @Test
    fun `checking an item sends only its state, so Mealie keeps its food and amount`() =
        runTest {
            val uid = gateway.addRemote(MEALIE, "250 grammes Pâtes")
            localItem("a", "Pâtes", 250.0, "g", remoteId = uid, checked = true)
            queue.enqueue(SyncOperationType.CHECK_ITEM, LIST, "a", remoteItemId = uid)

            engine.synchronize()

            val update = gateway.updates.single()
            assertNull(update.summary)
            assertEquals(true, update.completed)
            assertEquals("250 grammes Pâtes", gateway.remote(MEALIE).single().summary)
            assertEquals(250.0, item("Pâtes").quantity, 0.0)
        }

    @Test
    fun `an edited quantity is written in the text, never in a description`() =
        runTest {
            val uid = gateway.addRemote(MEALIE, "250 grammes Pâtes")
            localItem("a", "Pâtes", 300.0, "g", remoteId = uid)
            queue.enqueue(SyncOperationType.UPDATE_ITEM, LIST, "a", remoteItemId = uid)

            engine.synchronize()

            val update = gateway.updates.single()
            assertEquals("300 g Pâtes", update.summary)
            assertEquals(false, update.sendDescription)
            assertEquals(300.0, item("Pâtes").quantity, 0.0)
            assertTrue(queue.pending().isEmpty())
        }

    @Test
    fun `an item added in the app is written with its quantity and found again`() =
        runTest {
            localItem("a", "Pain", 2.0)
            queue.enqueue(SyncOperationType.CREATE_ITEM, LIST, "a")

            engine.synchronize()

            val remote = gateway.remote(MEALIE).single()
            assertEquals("2 Pain", remote.summary)
            assertEquals(remote.uid, item("Pain").remoteId)
            assertEquals(2.0, item("Pain").quantity, 0.0)
        }

    @Test
    fun `linking to a Mealie list adopts the same article without renaming it`() =
        runTest {
            gateway.addRemote(MEALIE, "250 grammes Pâtes")
            localItem("a", "Pâtes", 250.0, "g")
            queue.enqueue(SyncOperationType.CREATE_ITEM, LIST, "a")

            engine.synchronize()

            assertEquals("250 grammes Pâtes", gateway.remote(MEALIE).single().summary)
            assertTrue(gateway.updates.all { it.summary == null })
            assertEquals(gateway.remote(MEALIE).single().uid, item("Pâtes").remoteId)
        }

    @Test
    fun `items read before Mealie was supported take their article, the raw text leaves the catalog`() =
        runTest {
            val uid = gateway.addRemote(MEALIE, "250 grammes Pâtes")
            val raw = catalog.getOrCreateCustomProduct("250 grammes Pâtes").id
            localItem("a", "250 grammes Pâtes", remoteId = uid, productId = raw)

            engine.synchronize()

            val pates = item("Pâtes")
            assertEquals(250.0, pates.quantity, 0.0)
            assertEquals("g", pates.unit)
            assertEquals("seed:pates", pates.catalogProductId)
            assertEquals(listOf(raw), catalog.deletionRequests)
            assertTrue(catalog.candidates.none { it.product.id == raw })
            // Nothing is sent back: Mealie keeps its item as it is.
            assertTrue(gateway.updates.isEmpty())
        }

    @Test
    fun `an item in sync still filed under a custom product takes the product its text names`() =
        runTest {
            val uid = gateway.addRemote(MEALIE, "1 gousse ail")
            val raw = catalog.getOrCreateCustomProduct("gousse ail").id
            localItem("a", "gousse ail", remoteId = uid, productId = raw)

            engine.synchronize()

            assertEquals("seed:ail", item("gousse ail").catalogProductId)
            assertTrue(catalog.candidates.none { it.product.id == raw })
            assertTrue(gateway.updates.isEmpty())
        }

    @Test
    fun `a Mealie text without quantity keeps the local one`() =
        runTest {
            val uid = gateway.addRemote(MEALIE, "Pâtes")
            localItem("a", "Pâtes", 500.0, "g", remoteId = uid)

            engine.synchronize()

            assertEquals(500.0, item("Pâtes").quantity, 0.0)
            assertEquals("g", item("Pâtes").unit)
        }

    @Test
    fun `when the registry cannot be reached the list is read as before, and asked again later`() =
        runTest {
            gateway.addRemote(MEALIE, "2 Pain")
            registry.failure = HaErrorKind.UNREACHABLE

            assertEquals(SyncOutcome.Success, engine.synchronize())
            assertEquals("2 Pain", store.items.values.single().name)
            assertTrue(store.integrations.isEmpty())

            registry.failure = null
            engine.synchronize()

            assertEquals(2, registry.questions.size)
            assertEquals(2.0, item("Pain").quantity, 0.0)
        }

    @Test
    fun `a Home Assistant that cannot tell is not asked again`() =
        runTest {
            gateway.addRemote(MEALIE, "2 Pain")
            registry.failure = HaErrorKind.REJECTED

            engine.synchronize()
            engine.synchronize()

            assertEquals(1, registry.questions.size)
            assertEquals(mapOf(MEALIE to null), store.integrations)
            assertEquals("2 Pain", store.items.values.single().name)
        }

    @Test
    fun `other lists keep their texts, and lists with descriptions are never asked about`() =
        runTest {
            gateway.lists[MEALIE] = HaTodoList(MEALIE, "Mealie", supportsDescription = true)
            gateway.lists[SHOPPING] = HaTodoList(SHOPPING, "Shopping list", supportsDescription = false)
            store.lists[OTHER] = SyncListRef(OTHER, "Shopping list", SHOPPING)
            registry.integrations[SHOPPING] = "shopping_list"
            gateway.addRemote(SHOPPING, "2 Pain")

            engine.synchronize()

            assertEquals(listOf(listOf(SHOPPING)), registry.questions)
            val pain = store.items.values.single { it.listLocalId == OTHER }
            assertEquals("2 Pain", pain.name)
            assertEquals(1.0, pain.quantity, 0.0)
        }

    private companion object {
        const val LIST = "list-mealie"
        const val OTHER = "list-other"
        const val MEALIE = "todo.mealie_courses"
        const val SHOPPING = "todo.shopping_list"
    }
}
