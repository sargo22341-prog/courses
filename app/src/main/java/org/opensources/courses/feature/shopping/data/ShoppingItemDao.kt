package org.opensources.courses.feature.shopping.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.opensources.courses.core.model.SyncStatus

@Dao
interface ShoppingItemDao {
    @Query("SELECT * FROM shopping_items WHERE listLocalId = :listId AND isDeleted = 0 ORDER BY createdAt DESC")
    fun observeActiveForList(listId: String): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items WHERE listLocalId = :listId AND isDeleted = 0 ORDER BY createdAt DESC")
    suspend fun getActiveForList(listId: String): List<ShoppingItemEntity>

    /** Includes tombstones: used by synchronisation. */
    @Query("SELECT * FROM shopping_items WHERE listLocalId = :listId ORDER BY createdAt ASC")
    suspend fun getAllForList(listId: String): List<ShoppingItemEntity>

    @Query("SELECT * FROM shopping_items WHERE isDeleted = 0")
    suspend fun getAllActive(): List<ShoppingItemEntity>

    @Query("SELECT * FROM shopping_items WHERE localId = :id")
    suspend fun getById(id: String): ShoppingItemEntity?

    /** Changes the catalog link alone, only if the item still has [expectedName]; returns the rows changed. */
    @Query("UPDATE shopping_items SET catalogProductId = :catalogProductId WHERE localId = :id AND name = :expectedName")
    suspend fun updateCatalogProduct(
        id: String,
        expectedName: String,
        catalogProductId: String,
    ): Int

    @Insert
    suspend fun insert(item: ShoppingItemEntity)

    @Update
    suspend fun update(item: ShoppingItemEntity)

    @Query("DELETE FROM shopping_items WHERE localId = :id")
    suspend fun delete(id: String)

    /** Checked items of the list, tombstones apart; returns how many were deleted. */
    @Query("DELETE FROM shopping_items WHERE listLocalId = :listId AND isChecked = 1 AND isDeleted = 0")
    suspend fun deleteChecked(listId: String): Int

    /** Local deletions that will no longer be sent anywhere. */
    @Query("DELETE FROM shopping_items WHERE listLocalId = :listId AND isDeleted = 1")
    suspend fun deleteTombstones(listId: String)

    /** Forgets the remote ids of the list's items, which take [status]. */
    @Query("UPDATE shopping_items SET remoteId = NULL, syncStatus = :status WHERE listLocalId = :listId")
    suspend fun detachFromRemote(
        listId: String,
        status: SyncStatus,
    )
}
