package org.opensources.courses.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import org.opensources.courses.core.sync.SyncOperationDao
import org.opensources.courses.core.sync.SyncOperationEntity
import org.opensources.courses.feature.catalog.data.local.CatalogAliasEntity
import org.opensources.courses.feature.catalog.data.local.CatalogDao
import org.opensources.courses.feature.catalog.data.local.CatalogProductEntity
import org.opensources.courses.feature.catalog.data.local.ProductUsageEntity
import org.opensources.courses.feature.homeassistant.data.local.HaTrackedListDao
import org.opensources.courses.feature.homeassistant.data.local.HaTrackedListEntity
import org.opensources.courses.feature.lists.data.ShoppingListDao
import org.opensources.courses.feature.lists.data.ShoppingListEntity
import org.opensources.courses.feature.shopping.data.ShoppingItemDao
import org.opensources.courses.feature.shopping.data.ShoppingItemEntity

/**
 * Single local source of truth. The UI only ever reads this database (through repositories);
 * network results are written here first.
 *
 * Schemas are exported to `app/schemas`: any change to an entity must bump [version] and ship a
 * migration (or an `AutoMigration`) validated against the exported JSON.
 */
@Database(
    entities = [
        ShoppingListEntity::class,
        ShoppingItemEntity::class,
        CatalogProductEntity::class,
        CatalogAliasEntity::class,
        ProductUsageEntity::class,
        SyncOperationEntity::class,
        HaTrackedListEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class CoursesDatabase : RoomDatabase() {
    abstract fun shoppingListDao(): ShoppingListDao

    abstract fun shoppingItemDao(): ShoppingItemDao

    abstract fun catalogDao(): CatalogDao

    abstract fun syncOperationDao(): SyncOperationDao

    abstract fun haTrackedListDao(): HaTrackedListDao

    companion object {
        const val NAME = "courses.db"
    }
}
