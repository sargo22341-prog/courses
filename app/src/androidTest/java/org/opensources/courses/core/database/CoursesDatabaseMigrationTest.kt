package org.opensources.courses.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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

    private companion object {
        const val DB_NAME = "migration-test.db"
        const val DB_NAME_V2 = "migration-test-v2.db"
    }
}
