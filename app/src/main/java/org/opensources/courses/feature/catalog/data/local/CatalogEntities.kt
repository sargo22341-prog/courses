package org.opensources.courses.feature.catalog.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.opensources.courses.feature.catalog.domain.CatalogProduct
import org.opensources.courses.feature.catalog.domain.CatalogSource

/**
 * @property normalizedName [org.opensources.courses.feature.catalog.domain.TextNormalizer] form,
 * the only column searched.
 * @property catalogVersion version of the import that last wrote the row; rows of an older version
 * are removed at the end of an import.
 */
@Entity(tableName = "catalog_products", indices = [Index("normalizedName"), Index("source")])
data class CatalogProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val category: String?,
    val brand: String?,
    val parentId: String?,
    val source: CatalogSource,
    val baseScore: Int,
    val catalogVersion: String?,
)

@Entity(
    tableName = "catalog_aliases",
    foreignKeys = [
        ForeignKey(
            entity = CatalogProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("productId"), Index("normalizedAlias")],
)
data class CatalogAliasEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: String,
    val alias: String,
    val normalizedAlias: String,
)

/**
 * Personal habits, kept apart from products so a catalog refresh never resets them. Never sent
 * anywhere.
 */
@Entity(tableName = "product_usage")
data class ProductUsageEntity(
    @PrimaryKey val productId: String,
    val useCount: Int,
    val lastUsedAt: Long,
)

data class ProductCandidateRow(
    @Embedded val product: CatalogProductEntity,
    val useCount: Int,
    val lastUsedAt: Long?,
)

fun CatalogProductEntity.toDomain(): CatalogProduct =
    CatalogProduct(
        id = id,
        name = name,
        category = category,
        brand = brand,
        parentId = parentId,
        source = source,
    )
