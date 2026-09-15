package org.opensources.courses.feature.lists.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.opensources.courses.core.model.SyncStatus
import org.opensources.courses.feature.lists.domain.ShoppingList

/**
 * @property remoteId Home Assistant `todo.*` entity id when linked.
 * @property remoteEntryId Home Assistant config entry id, known only for lists created by the app
 * (needed to delete them remotely).
 * @property importedFromRemote added by the remote itself (Home Assistant "all lists" mode) rather
 * than created by the user: it only mirrors the remote list.
 * @property remoteName remote name last applied to an imported list, so that a rename made remotely
 * is told apart from a rename made in the app.
 */
@Entity(tableName = "shopping_lists")
data class ShoppingListEntity(
    @PrimaryKey val localId: String,
    val name: String,
    val isDefault: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val remoteId: String? = null,
    val remoteEntryId: String? = null,
    val createdByApp: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    @ColumnInfo(defaultValue = "0") val importedFromRemote: Boolean = false,
    val remoteName: String? = null,
)

fun ShoppingListEntity.toDomain(): ShoppingList =
    ShoppingList(
        id = localId,
        name = name,
        isDefault = isDefault,
        remoteId = remoteId,
        createdByApp = createdByApp,
        syncStatus = syncStatus,
        importedFromRemote = importedFromRemote,
    )
