package org.opensources.courses.feature.homeassistant.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * Integration providing a Home Assistant list (`mealie`, `shopping_list`…), as the entity registry
 * told it once: an entity keeps its integration, so it is never asked again. [integration] is null
 * when Home Assistant cannot tell (entity absent from the registry, command unknown).
 */
@Entity(tableName = "ha_list_integrations")
data class HaListIntegrationEntity(
    @PrimaryKey val entityId: String,
    val integration: String?,
)

@Dao
interface HaListIntegrationDao {
    @Upsert
    suspend fun upsert(integrations: List<HaListIntegrationEntity>)

    @Query("SELECT * FROM ha_list_integrations WHERE entityId IN (:entityIds)")
    suspend fun getByEntityIds(entityIds: List<String>): List<HaListIntegrationEntity>

    @Query("DELETE FROM ha_list_integrations")
    suspend fun deleteAll()
}
