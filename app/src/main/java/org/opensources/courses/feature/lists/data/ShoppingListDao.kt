package org.opensources.courses.feature.lists.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingListDao {
    @Query("SELECT * FROM shopping_lists ORDER BY position ASC, isDefault DESC, createdAt ASC")
    fun observeAll(): Flow<List<ShoppingListEntity>>

    @Query("SELECT * FROM shopping_lists WHERE localId = :id")
    fun observeById(id: String): Flow<ShoppingListEntity?>

    @Query("SELECT * FROM shopping_lists WHERE isDefault = 1 LIMIT 1")
    fun observeDefault(): Flow<ShoppingListEntity?>

    @Query("SELECT * FROM shopping_lists WHERE localId = :id")
    suspend fun getById(id: String): ShoppingListEntity?

    @Query("SELECT * FROM shopping_lists WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): ShoppingListEntity?

    @Query("SELECT * FROM shopping_lists ORDER BY createdAt ASC")
    suspend fun getAll(): List<ShoppingListEntity>

    @Query("SELECT * FROM shopping_lists WHERE syncStatus != 'LOCAL_ONLY' ORDER BY createdAt ASC")
    suspend fun getSynchronized(): List<ShoppingListEntity>

    @Insert
    suspend fun insert(list: ShoppingListEntity)

    @Update
    suspend fun update(list: ShoppingListEntity)

    @Query("DELETE FROM shopping_lists WHERE localId = :id")
    suspend fun delete(id: String)

    @Query("UPDATE shopping_lists SET isDefault = (localId = :id)")
    suspend fun setDefault(id: String)

    /** After every existing list: a new list goes to the end. */
    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM shopping_lists")
    suspend fun nextPosition(): Int

    @Query("UPDATE shopping_lists SET position = :position WHERE localId = :id")
    suspend fun setPosition(
        id: String,
        position: Int,
    )
}
