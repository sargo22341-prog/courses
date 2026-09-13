package org.opensources.courses.testing

import android.content.Context
import androidx.room.Room
import org.opensources.courses.core.database.CoursesDatabase
import org.opensources.courses.core.database.RoomTransactionRunner
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
    private val transactions = RoomTransactionRunner(database)
    val lists = ShoppingListRepositoryImpl(database.shoppingListDao(), queue, transactions, clock)
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
