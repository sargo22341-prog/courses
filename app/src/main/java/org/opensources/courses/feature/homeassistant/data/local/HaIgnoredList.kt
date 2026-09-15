package org.opensources.courses.feature.homeassistant.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * A Home Assistant list the user removed from the app or unlinked: the "all lists" mode does not
 * import it again. Linking it by hand forgets this choice.
 */
@Entity(tableName = "ha_ignored_lists")
data class HaIgnoredListEntity(
    @PrimaryKey val entityId: String,
    val ignoredAt: Long,
)

@Dao
interface HaIgnoredListDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(list: HaIgnoredListEntity)

    @Query("SELECT entityId FROM ha_ignored_lists")
    suspend fun getEntityIds(): List<String>

    @Query("DELETE FROM ha_ignored_lists WHERE entityId = :entityId")
    suspend fun delete(entityId: String)
}
