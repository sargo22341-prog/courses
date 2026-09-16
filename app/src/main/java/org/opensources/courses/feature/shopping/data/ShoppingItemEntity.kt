package org.opensources.courses.feature.shopping.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.opensources.courses.core.model.SyncStatus
import org.opensources.courses.feature.catalog.domain.TextNormalizer
import org.opensources.courses.feature.lists.data.ShoppingListEntity
import org.opensources.courses.feature.shopping.domain.ShoppingItem

/**
 * @property remoteId Home Assistant to-do item `uid`, once known.
 * @property isDeleted tombstone: the item was deleted locally but the deletion has not reached
 * Home Assistant yet. Tombstones are hidden from the UI and purged after synchronisation.
 */
@Entity(
    tableName = "shopping_items",
    foreignKeys = [
        ForeignKey(
            entity = ShoppingListEntity::class,
            parentColumns = ["localId"],
            childColumns = ["listLocalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("listLocalId"), Index("remoteId")],
)
data class ShoppingItemEntity(
    @PrimaryKey val localId: String,
    val listLocalId: String,
    val name: String,
    val quantity: Double,
    val unit: String?,
    val isChecked: Boolean,
    val catalogProductId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val remoteId: String? = null,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    val isDeleted: Boolean = false,
)

/**
 * The item under [name]. Another name may stand for another product, so the catalog link is dropped
 * unless only case, accents or punctuation changed; the link is found again from the new name.
 */
fun ShoppingItemEntity.renamed(name: String): ShoppingItemEntity =
    copy(name = name, catalogProductId = catalogProductId.takeIf { TextNormalizer.normalize(name) == TextNormalizer.normalize(this.name) })

fun ShoppingItemEntity.toDomain(): ShoppingItem =
    ShoppingItem(
        id = localId,
        listId = listLocalId,
        name = name,
        quantity = quantity,
        unit = unit,
        isChecked = isChecked,
        catalogProductId = catalogProductId,
    )
