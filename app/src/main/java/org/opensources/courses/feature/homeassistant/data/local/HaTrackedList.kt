package org.opensources.courses.feature.homeassistant.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A list created in Home Assistant by this application. Kept even when the local list is unlinked,
 * so the "only lists created by this application" mode can still offer it.
 */
@Entity(tableName = "ha_tracked_lists")
data class HaTrackedListEntity(
    @PrimaryKey val entityId: String,
    val configEntryId: String?,
    val name: String,
    val createdAt: Long,
)

@Dao
interface HaTrackedListDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(list: HaTrackedListEntity)

    @Query("SELECT * FROM ha_tracked_lists")
    fun observeAll(): Flow<List<HaTrackedListEntity>>

    @Query("SELECT * FROM ha_tracked_lists WHERE entityId = :entityId")
    suspend fun getByEntityId(entityId: String): HaTrackedListEntity?

    @Query("DELETE FROM ha_tracked_lists WHERE entityId = :entityId")
    suspend fun delete(entityId: String)
}
