package org.opensources.courses.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Migrations of [CoursesDatabase] that Room cannot generate as an `AutoMigration`. */
object CoursesDatabaseMigrations {
    /**
     * 4: columns that were written but never read are dropped. They are dropped in place: an
     * `AutoMigration` would rebuild each table, including `catalog_products`, which the aliases
     * reference, for no benefit. No row is touched.
     */
    val FROM_3_TO_4: Migration =
        object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                DROPPED_IN_4.forEach { (table, column) -> db.execSQL("ALTER TABLE `$table` DROP COLUMN `$column`") }
            }
        }

    val ALL: Array<Migration> = arrayOf(FROM_3_TO_4)

    private val DROPPED_IN_4 =
        listOf(
            "catalog_products" to "brand",
            "catalog_products" to "parentId",
            "shopping_items" to "version",
            "sync_operations" to "lastError",
            "ha_tracked_lists" to "name",
            "ha_tracked_lists" to "createdAt",
        )
}
