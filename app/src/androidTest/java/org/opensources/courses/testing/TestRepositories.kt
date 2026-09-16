package org.opensources.courses.testing

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.opensources.courses.core.database.CoursesDatabase
import org.opensources.courses.core.database.RoomTransactionRunner
import org.opensources.courses.core.sync.RemoteChange
import org.opensources.courses.core.sync.RemoteSyncEngine
import org.opensources.courses.core.sync.SyncOutcome
import org.opensources.courses.core.sync.SyncQueue
import org.opensources.courses.core.sync.SyncRequest
import org.opensources.courses.feature.catalog.data.CatalogRepositoryImpl
import org.opensources.courses.feature.homeassistant.data.HaListLinkRepositoryImpl
import org.opensources.courses.feature.homeassistant.data.HaLocalListWriter
import org.opensources.courses.feature.homeassistant.data.sync.RoomSyncLocalStore
import org.opensources.courses.feature.lists.data.ShoppingListRepositoryImpl
import org.opensources.courses.feature.shopping.data.ShoppingItemRepositoryImpl
import java.time.Clock

/** The production repositories wired on a real Room database, without Hilt and without network. */
class TestRepositories(
    val database: CoursesDatabase,
    clock: Clock = Clock.systemUTC(),
) {
    val queue = SyncQueue(database.syncOperationDao(), clock)
    val remoteSync = FakeRemoteSyncEngine()
    private val transactions = RoomTransactionRunner(database)
    private val listDao = database.shoppingListDao()
    private val itemDao = database.shoppingItemDao()
    private val writer = HaLocalListWriter(listDao, itemDao, database.haIgnoredListDao(), queue, clock)
    val lists = ShoppingListRepositoryImpl(listDao, queue, remoteSync, transactions, clock)
    val items = ShoppingItemRepositoryImpl(itemDao, listDao, queue, transactions, clock)
    val links = HaListLinkRepositoryImpl(listDao, itemDao, database.haTrackedListDao(), writer, queue, transactions, clock)
    val syncStore = RoomSyncLocalStore(listDao, itemDao, database.haTrackedListDao(), writer, queue, transactions, clock)
    val catalog = CatalogRepositoryImpl(database.catalogDao(), transactions, clock)

    companion object {
        fun inMemory(context: Context) = TestRepositories(Room.inMemoryDatabaseBuilder(context, CoursesDatabase::class.java).build())

        fun onDisk(
            context: Context,
            name: String,
        ) = TestRepositories(Room.databaseBuilder(context, CoursesDatabase::class.java, name).build())
    }
}

/** No remote at all: tests only choose whether new lists are synchronised. */
class FakeRemoteSyncEngine(
    var newListsSynchronized: Boolean = false,
) : RemoteSyncEngine {
    override val isEnabled: Flow<Boolean> = flowOf(newListsSynchronized)

    override val isAutoSyncEnabled: Flow<Boolean> = flowOf(false)

    override val remoteChanges: Flow<RemoteChange> = emptyFlow()

    override suspend fun synchronizesNewLists(): Boolean = newListsSynchronized

    override suspend fun synchronize(request: SyncRequest): SyncOutcome = SyncOutcome.Skipped
}
