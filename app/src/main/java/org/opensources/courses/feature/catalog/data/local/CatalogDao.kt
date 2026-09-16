package org.opensources.courses.feature.catalog.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Query(
        """
        SELECT p.id, p.name, p.normalizedName, p.category, p.source, p.baseScore,
               COALESCE(u.useCount, 0) AS useCount, u.lastUsedAt AS lastUsedAt
        FROM catalog_products p
        LEFT JOIN product_usage u ON u.productId = p.id
        WHERE p.normalizedName LIKE '%' || :query || '%'
           OR p.id IN (SELECT productId FROM catalog_aliases WHERE normalizedAlias LIKE '%' || :query || '%')
        ORDER BY useCount DESC, p.baseScore DESC, LENGTH(p.name) ASC
        LIMIT :limit
        """,
    )
    suspend fun search(
        query: String,
        limit: Int,
    ): List<ProductCandidateRow>

    @Query(
        """
        SELECT p.id, p.name, p.normalizedName, p.category, p.source, p.baseScore,
               COALESCE(u.useCount, 0) AS useCount, u.lastUsedAt AS lastUsedAt
        FROM catalog_products p
        LEFT JOIN product_usage u ON u.productId = p.id
        WHERE p.normalizedName LIKE :initial || '%' OR p.normalizedName LIKE '% ' || :initial || '%'
        ORDER BY useCount DESC, p.baseScore DESC
        LIMIT :limit
        """,
    )
    suspend fun fuzzyCandidates(
        initial: String,
        limit: Int,
    ): List<ProductCandidateRow>

    @Query("SELECT productId, normalizedAlias FROM catalog_aliases WHERE productId IN (:productIds)")
    suspend fun aliasesFor(productIds: List<String>): List<ProductAliasRow>

    @Query(
        """
        SELECT * FROM catalog_products WHERE normalizedName = :normalizedName
        ORDER BY CASE source WHEN 'CUSTOM' THEN 0 WHEN 'SEED' THEN 1 ELSE 2 END
        LIMIT 1
        """,
    )
    suspend fun findByNormalizedName(normalizedName: String): CatalogProductEntity?

    @Insert
    suspend fun insertProduct(product: CatalogProductEntity)

    @Upsert
    suspend fun upsertProducts(products: List<CatalogProductEntity>)

    @Insert
    suspend fun insertAliases(aliases: List<CatalogAliasEntity>)

    @Query("DELETE FROM catalog_aliases WHERE productId IN (SELECT id FROM catalog_products WHERE source = :source)")
    suspend fun deleteAliasesForSource(source: String)

    @Query("UPDATE catalog_products SET catalogVersion = NULL WHERE source = :source")
    suspend fun markSourceOutdated(source: String)

    @Query("DELETE FROM catalog_products WHERE source = :source AND (catalogVersion IS NULL OR catalogVersion != :version)")
    suspend fun deleteOutdated(
        source: String,
        version: String,
    )

    @Query(
        """
        INSERT INTO product_usage (productId, useCount, lastUsedAt) VALUES (:productId, 1, :now)
        ON CONFLICT(productId) DO UPDATE SET useCount = useCount + 1, lastUsedAt = :now
        """,
    )
    suspend fun recordUsage(
        productId: String,
        now: Long,
    )

    /** Products whose usage outlived a catalog import are left out: they can no longer be added by id. */
    @Query(
        """
        SELECT p.id, p.name, p.normalizedName, p.category
        FROM product_usage u
        JOIN catalog_products p ON p.id = u.productId
        ORDER BY u.useCount DESC, u.lastUsedAt DESC
        LIMIT :limit
        """,
    )
    fun observeFrequent(limit: Int): Flow<List<FrequentProductRow>>

    @Query("SELECT EXISTS(SELECT 1 FROM product_usage u JOIN catalog_products p ON p.id = u.productId)")
    fun observeHasUsage(): Flow<Boolean>

    @Query("DELETE FROM product_usage")
    suspend fun clearUsage()

    @Query("SELECT COUNT(*) FROM catalog_products")
    fun observeCount(): Flow<Int>

    @Query(
        """
        SELECT normalizedName, groceryCategory, source FROM catalog_products
        WHERE groceryCategory IS NOT NULL AND normalizedName IN (:normalizedNames)
        """,
    )
    fun observeCategories(normalizedNames: List<String>): Flow<List<ProductCategoryRow>>

    @Query("SELECT id, normalizedName, source FROM catalog_products WHERE normalizedName IN (:normalizedNames)")
    suspend fun findRefsByNormalizedNames(normalizedNames: List<String>): List<ProductRefRow>

    @Query("SELECT id, normalizedName, source FROM catalog_products WHERE id IN (:ids)")
    suspend fun findRefsByIds(ids: List<String>): List<ProductRefRow>

    @Query("SELECT id, groceryCategory FROM catalog_products WHERE groceryCategory IS NOT NULL AND id IN (:ids)")
    fun observeCategoriesByIds(ids: List<String>): Flow<List<ProductIdCategoryRow>>
}
