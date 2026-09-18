package org.opensources.courses.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import org.opensources.courses.core.sync.SyncOperationDao
import org.opensources.courses.core.sync.SyncOperationEntity
import org.opensources.courses.feature.catalog.data.local.CatalogAliasEntity
import org.opensources.courses.feature.catalog.data.local.CatalogDao
import org.opensources.courses.feature.catalog.data.local.CatalogProductEntity
import org.opensources.courses.feature.catalog.data.local.ProductUsageEntity
import org.opensources.courses.feature.homeassistant.data.local.HaIgnoredListDao
import org.opensources.courses.feature.homeassistant.data.local.HaIgnoredListEntity
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
 *
 * - 2: `catalog_products.groceryCategory` (shop sections), a nullable column filled by the next
 *   seed and OpenFoodFacts imports.
 * - 3: `shopping_lists.importedFromRemote` and `remoteName` (lists imported from Home Assistant),
 *   table `ha_ignored_lists`. Existing lists are not imported ones.
 * - 4: columns never read are dropped ([CoursesDatabaseMigrations.FROM_3_TO_4]).
 * - 5: `shopping_lists.position`, the order chosen by the user. Existing lists all get 0 and keep
 *   their former order (default list first, then by creation).
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
        HaIgnoredListEntity::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 4, to = 5)],
)
abstract class CoursesDatabase : RoomDatabase() {
    abstract fun shoppingListDao(): ShoppingListDao

    abstract fun shoppingItemDao(): ShoppingItemDao

    abstract fun catalogDao(): CatalogDao

    abstract fun syncOperationDao(): SyncOperationDao

    abstract fun haTrackedListDao(): HaTrackedListDao

    abstract fun haIgnoredListDao(): HaIgnoredListDao

    companion object {
        const val NAME = "courses.db"
    }
}
