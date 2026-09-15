package org.opensources.courses.testing

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.opensources.courses.core.database.CoursesDatabase
import org.opensources.courses.core.database.RoomTransactionRunner
import org.opensources.courses.core.sync.RemoteSyncEngine
import org.opensources.courses.core.sync.SyncOutcome
import org.opensources.courses.core.sync.SyncQueue
import org.opensources.courses.feature.catalog.data.CatalogRepositoryImpl
import org.opensources.courses.feature.homeassistant.data.HaListLinkRepositoryImpl
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
    val lists = ShoppingListRepositoryImpl(database.shoppingListDao(), queue, remoteSync, transactions, clock)
    val items = ShoppingItemRepositoryImpl(database.shoppingItemDao(), database.shoppingListDao(), queue, transactions, clock)
    val links = HaListLinkRepositoryImpl(database.shoppingListDao(), database.shoppingItemDao(), database.haTrackedListDao(), queue, transactions, clock)
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

    override val remoteChanges: Flow<Unit> = emptyFlow()

    override suspend fun synchronizesNewLists(): Boolean = newListsSynchronized

    override suspend fun synchronize(): SyncOutcome = SyncOutcome.Skipped
}
