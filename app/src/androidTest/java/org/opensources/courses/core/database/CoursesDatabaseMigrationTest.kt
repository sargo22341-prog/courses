package org.opensources.courses.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** User data written by version 1 must survive every migration, validated against `app/schemas`. */
@RunWith(AndroidJUnit4::class)
class CoursesDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), CoursesDatabase::class.java)

    @Test
    fun version1DataSurvivesAndProductsGainAnEmptyShopSection() {
        helper.createDatabase(DB_NAME, 1).use { db ->
            db.execSQL(
                "INSERT INTO shopping_lists (localId, name, isDefault, createdAt, updatedAt, remoteId, remoteEntryId, createdByApp, syncStatus) " +
                    "VALUES ('l1', 'Courses', 1, 0, 0, NULL, NULL, 0, 'LOCAL_ONLY')",
            )
            db.execSQL(
                "INSERT INTO shopping_items (localId, listLocalId, name, quantity, unit, isChecked, catalogProductId, createdAt, updatedAt, " +
                    "version, remoteId, syncStatus, isDeleted) VALUES ('i1', 'l1', 'Lait', 2.0, NULL, 0, 'seed:lait', 0, 0, 1, NULL, 'LOCAL_ONLY', 0)",
            )
            db.execSQL(
                "INSERT INTO catalog_products (id, name, normalizedName, category, brand, parentId, source, baseScore, catalogVersion) " +
                    "VALUES ('seed:lait', 'Lait', 'lait', 'Produits laitiers', NULL, NULL, 'SEED', 8, 'seed-1')",
            )
            db.execSQL("INSERT INTO product_usage (productId, useCount, lastUsedAt) VALUES ('seed:lait', 4, 10)")
        }

        helper.runMigrationsAndValidate(DB_NAME, 2, true).use { db ->
            db.query("SELECT name, quantity FROM shopping_items WHERE localId = 'i1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Lait", cursor.getString(0))
                assertEquals(2.0, cursor.getDouble(1), 0.0)
            }
            db.query("SELECT groceryCategory FROM catalog_products WHERE id = 'seed:lait'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
            }
            db.query("SELECT useCount FROM product_usage WHERE productId = 'seed:lait'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(4, cursor.getInt(0))
            }
        }
    }

    @Test
    fun version2ListsAreKeptAndAreNotImportedLists() {
        helper.createDatabase(DB_NAME_V2, 2).use { db ->
            db.execSQL(
                "INSERT INTO shopping_lists (localId, name, isDefault, createdAt, updatedAt, remoteId, remoteEntryId, createdByApp, syncStatus) " +
                    "VALUES ('l1', 'Courses', 1, 0, 0, 'todo.courses', NULL, 0, 'SYNCED')",
            )
        }

        helper.runMigrationsAndValidate(DB_NAME_V2, 3, true).use { db ->
            db.query("SELECT name, remoteId, importedFromRemote, remoteName FROM shopping_lists WHERE localId = 'l1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Courses", cursor.getString(0))
                assertEquals("todo.courses", cursor.getString(1))
                assertEquals(0, cursor.getInt(2))
                assertTrue(cursor.isNull(3))
            }
            db.query("SELECT COUNT(*) FROM ha_ignored_lists").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun version3DataSurvivesTheRemovalOfUnreadColumns() {
        helper.createDatabase(DB_NAME_V3, 3).use { db ->
            db.execSQL(
                "INSERT INTO shopping_lists (localId, name, isDefault, createdAt, updatedAt, remoteId, remoteEntryId, createdByApp, syncStatus, " +
                    "importedFromRemote, remoteName) VALUES ('l1', 'Courses', 1, 0, 0, 'todo.courses', 'entry-1', 1, 'SYNCED', 0, NULL)",
            )
            db.execSQL(
                "INSERT INTO shopping_items (localId, listLocalId, name, quantity, unit, isChecked, catalogProductId, createdAt, updatedAt, " +
                    "version, remoteId, syncStatus, isDeleted) VALUES ('i1', 'l1', 'Lait', 2.5, 'L', 1, 'seed:lait', 5, 6, 3, 'uid-1', 'PENDING', 0)",
            )
            db.execSQL(
                "INSERT INTO catalog_products (id, name, normalizedName, category, brand, parentId, source, baseScore, catalogVersion, groceryCategory) " +
                    "VALUES ('seed:lait', 'Lait', 'lait', 'Produits laitiers', NULL, NULL, 'SEED', 8, 'seed-3-fr', 'DAIRY_EGGS')",
            )
            db.execSQL("INSERT INTO catalog_aliases (productId, alias, normalizedAlias) VALUES ('seed:lait', 'Lolo', 'lolo')")
            db.execSQL("INSERT INTO product_usage (productId, useCount, lastUsedAt) VALUES ('seed:lait', 4, 10)")
            db.execSQL(
                "INSERT INTO sync_operations (type, listLocalId, itemLocalId, remoteListId, remoteItemId, remoteEntryId, createdAt, attemptCount, lastError) " +
                    "VALUES ('CHECK_ITEM', 'l1', 'i1', NULL, 'uid-1', NULL, 7, 2, 'REJECTED')",
            )
            db.execSQL("INSERT INTO ha_tracked_lists (entityId, configEntryId, name, createdAt) VALUES ('todo.courses', 'entry-1', 'Courses', 1)")
            db.execSQL("INSERT INTO ha_ignored_lists (entityId, ignoredAt) VALUES ('todo.old', 2)")
        }

        helper.runMigrationsAndValidate(DB_NAME_V3, 4, true, *CoursesDatabaseMigrations.ALL).use { db ->
            db.assertRow("SELECT name, quantity, unit, isChecked, remoteId, syncStatus, updatedAt FROM shopping_items", "Lait", "2.5", "L", "1", "uid-1", "PENDING", "6")
            db.assertRow("SELECT name, normalizedName, groceryCategory, catalogVersion FROM catalog_products", "Lait", "lait", "DAIRY_EGGS", "seed-3-fr")
            db.assertRow("SELECT productId, normalizedAlias FROM catalog_aliases", "seed:lait", "lolo")
            db.assertRow("SELECT useCount FROM product_usage", "4")
            db.assertRow("SELECT type, itemLocalId, remoteItemId, attemptCount FROM sync_operations", "CHECK_ITEM", "i1", "uid-1", "2")
            db.assertRow("SELECT entityId, configEntryId FROM ha_tracked_lists", "todo.courses", "entry-1")
            db.assertRow("SELECT entityId FROM ha_ignored_lists", "todo.old")
            db.assertRow("SELECT name, remoteId, remoteEntryId FROM shopping_lists", "Courses", "todo.courses", "entry-1")
            assertFalse("brand" in db.columns("catalog_products"))
            assertFalse("parentId" in db.columns("catalog_products"))
            assertFalse("version" in db.columns("shopping_items"))
            assertFalse("lastError" in db.columns("sync_operations"))
            assertEquals(setOf("entityId", "configEntryId"), db.columns("ha_tracked_lists"))
        }
    }

    @Test
    fun version4ListsKeepTheirOrderAndGainAPosition() {
        helper.createDatabase(DB_NAME_V4, 4).use { db ->
            db.execSQL(
                "INSERT INTO shopping_lists (localId, name, isDefault, createdAt, updatedAt, remoteId, remoteEntryId, createdByApp, syncStatus, " +
                    "importedFromRemote, remoteName) VALUES ('l1', 'Courses', 1, 0, 0, NULL, NULL, 0, 'LOCAL_ONLY', 0, NULL)",
            )
            db.execSQL(
                "INSERT INTO shopping_lists (localId, name, isDefault, createdAt, updatedAt, remoteId, remoteEntryId, createdByApp, syncStatus, " +
                    "importedFromRemote, remoteName) VALUES ('l2', 'BBQ', 0, 5, 5, 'todo.bbq', NULL, 0, 'SYNCED', 1, 'BBQ')",
            )
        }

        helper.runMigrationsAndValidate(DB_NAME_V4, 5, true, *CoursesDatabaseMigrations.ALL).use { db ->
            db.query("SELECT localId, name, position FROM shopping_lists ORDER BY position ASC, isDefault DESC, createdAt ASC").use { cursor ->
                val rows = buildList { while (cursor.moveToNext()) add(listOf(cursor.getString(0), cursor.getString(1), cursor.getString(2))) }
                assertEquals(listOf(listOf("l1", "Courses", "0"), listOf("l2", "BBQ", "0")), rows)
            }
        }
    }

    /** The query returns exactly one row, whose columns read as [expected]. */
    private fun SupportSQLiteDatabase.assertRow(
        sql: String,
        vararg expected: String,
    ) {
        query(sql).use { cursor ->
            assertEquals(sql, 1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals(sql, expected.toList(), List(cursor.columnCount) { cursor.getString(it) })
        }
    }

    private fun SupportSQLiteDatabase.columns(table: String): Set<String> =
        query("PRAGMA table_info(`$table`)").use { cursor ->
            val name = cursor.getColumnIndexOrThrow("name")
            buildSet { while (cursor.moveToNext()) add(cursor.getString(name)) }
        }

    private companion object {
        const val DB_NAME = "migration-test.db"
        const val DB_NAME_V2 = "migration-test-v2.db"
        const val DB_NAME_V3 = "migration-test-v3.db"
        const val DB_NAME_V4 = "migration-test-v4.db"
    }
}
