package org.opensources.courses.feature.shopping.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingItemDao {
    @Query("SELECT * FROM shopping_items WHERE listLocalId = :listId AND isDeleted = 0 ORDER BY createdAt DESC")
    fun observeActiveForList(listId: String): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items WHERE listLocalId = :listId AND isDeleted = 0 ORDER BY createdAt DESC")
    suspend fun getActiveForList(listId: String): List<ShoppingItemEntity>

    /** Includes tombstones: used by synchronisation. */
    @Query("SELECT * FROM shopping_items WHERE listLocalId = :listId ORDER BY createdAt ASC")
    suspend fun getAllForList(listId: String): List<ShoppingItemEntity>

    @Query("SELECT * FROM shopping_items WHERE localId = :id")
    suspend fun getById(id: String): ShoppingItemEntity?

    @Insert
    suspend fun insert(item: ShoppingItemEntity)

    @Update
    suspend fun update(item: ShoppingItemEntity)

    @Query("DELETE FROM shopping_items WHERE localId = :id")
    suspend fun delete(id: String)
}
