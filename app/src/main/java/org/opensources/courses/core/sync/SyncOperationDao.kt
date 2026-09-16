package org.opensources.courses.core.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncOperationDao {
    @Insert
    suspend fun insert(operation: SyncOperationEntity): Long

    @Query("SELECT * FROM sync_operations ORDER BY id ASC")
    suspend fun getAll(): List<SyncOperationEntity>

    @Query("SELECT COUNT(*) FROM sync_operations")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM sync_operations WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE sync_operations SET attemptCount = attemptCount + 1 WHERE id IN (:ids)")
    suspend fun markFailed(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM sync_operations WHERE itemLocalId = :itemLocalId")
    suspend fun countForItem(itemLocalId: String): Int

    @Query("SELECT DISTINCT itemLocalId FROM sync_operations WHERE listLocalId = :listLocalId AND itemLocalId IS NOT NULL")
    suspend fun getItemIdsForList(listLocalId: String): List<String>

    @Query("DELETE FROM sync_operations WHERE listLocalId = :listLocalId")
    suspend fun deleteForList(listLocalId: String)

    @Query("DELETE FROM sync_operations")
    suspend fun deleteAll()
}
