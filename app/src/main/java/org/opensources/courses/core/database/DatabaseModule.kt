package org.opensources.courses.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.opensources.courses.core.sync.SyncOperationDao
import org.opensources.courses.feature.catalog.data.local.CatalogDao
import org.opensources.courses.feature.homeassistant.data.local.HaIgnoredListDao
import org.opensources.courses.feature.homeassistant.data.local.HaTrackedListDao
import org.opensources.courses.feature.lists.data.ShoppingListDao
import org.opensources.courses.feature.shopping.data.ShoppingItemDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): CoursesDatabase = Room.databaseBuilder(context, CoursesDatabase::class.java, CoursesDatabase.NAME).build()

    @Provides
    fun transactionRunner(database: CoursesDatabase): TransactionRunner = RoomTransactionRunner(database)

    @Provides
    fun shoppingListDao(database: CoursesDatabase): ShoppingListDao = database.shoppingListDao()

    @Provides
    fun shoppingItemDao(database: CoursesDatabase): ShoppingItemDao = database.shoppingItemDao()

    @Provides
    fun catalogDao(database: CoursesDatabase): CatalogDao = database.catalogDao()

    @Provides
    fun syncOperationDao(database: CoursesDatabase): SyncOperationDao = database.syncOperationDao()

    @Provides
    fun haTrackedListDao(database: CoursesDatabase): HaTrackedListDao = database.haTrackedListDao()

    @Provides
    fun haIgnoredListDao(database: CoursesDatabase): HaIgnoredListDao = database.haIgnoredListDao()
}
