package org.opensources.courses.feature.catalog.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.opensources.courses.feature.catalog.domain.CatalogProduct
import org.opensources.courses.feature.catalog.domain.CatalogProductRef
import org.opensources.courses.feature.catalog.domain.CatalogSource
import org.opensources.courses.feature.catalog.domain.GroceryCategory
import org.opensources.courses.feature.catalog.domain.ProductSuggestion

/**
 * @property normalizedName [org.opensources.courses.feature.catalog.domain.TextNormalizer] form,
 * the only column searched.
 * @property catalogVersion version of the import that last wrote the row; rows of the source that an
 * import did not write are removed at its end.
 * @property groceryCategory shop section (database version 2); null for custom products and for
 * products the source cannot place.
 */
@Entity(tableName = "catalog_products", indices = [Index("normalizedName"), Index("source")])
data class CatalogProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val category: String?,
    val source: CatalogSource,
    val baseScore: Int,
    val catalogVersion: String?,
    val groceryCategory: GroceryCategory? = null,
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

/** What the autocomplete ranking reads of a product and of its usage, and nothing more. */
data class ProductCandidateRow(
    val id: String,
    val name: String,
    val normalizedName: String,
    val category: String?,
    val source: CatalogSource,
    val baseScore: Int,
    val useCount: Int,
    val lastUsedAt: Long?,
)

/** A product of the personal history, as the history list shows it. */
data class FrequentProductRow(
    val id: String,
    val name: String,
    val normalizedName: String,
    val category: String?,
) {
    fun toSuggestion() = ProductSuggestion(id, name, category, normalizedName)
}

data class ProductAliasRow(
    val productId: String,
    val normalizedAlias: String,
)

data class ProductCategoryRow(
    val normalizedName: String,
    val groceryCategory: GroceryCategory,
    val source: CatalogSource,
)

data class ProductIdCategoryRow(
    val id: String,
    val groceryCategory: GroceryCategory,
)

data class ProductRefRow(
    val id: String,
    val normalizedName: String,
    val source: CatalogSource,
) {
    fun toDomain() = CatalogProductRef(id, normalizedName, source)
}

fun CatalogProductEntity.toDomain(): CatalogProduct =
    CatalogProduct(
        id = id,
        name = name,
        category = category,
        source = source,
    )
