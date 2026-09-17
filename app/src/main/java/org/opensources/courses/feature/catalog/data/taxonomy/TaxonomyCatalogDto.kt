package org.opensources.courses.feature.catalog.data.taxonomy

import kotlinx.serialization.Serializable
import org.opensources.courses.feature.catalog.domain.CatalogImportProduct
import org.opensources.courses.feature.catalog.domain.GroceryCategory

/**
 * One generated file per language, written by `scripts/generate-catalog.py` from the OpenFoodFacts
 * categories taxonomy. Everything the import needs is already computed there: names are cleaned and
 * de-duplicated, shop sections are read on the whole parent graph and aliases come from the
 * synonyms of the taxonomy, which its published JSON does not carry.
 *
 * @property version format of the file, [AssetTaxonomyCatalogSource.VERSION] (guarded by a test).
 */
@Serializable
data class TaxonomyCatalogDto(
    val version: Int,
    val language: String,
    val products: List<TaxonomyProductDto>,
)

/**
 * @property score generic categories (close to a root) rank above specific ones.
 * @property category display name of the shelf it belongs to in the taxonomy (`Produits laitiers`).
 * @property section shop section, absent when the taxonomy cannot place the product.
 */
@Serializable
data class TaxonomyProductDto(
    val id: String,
    val name: String,
    val score: Int,
    val category: String? = null,
    val section: GroceryCategory? = null,
    val aliases: List<String> = emptyList(),
)

fun TaxonomyProductDto.toDomain(): CatalogImportProduct =
    CatalogImportProduct(
        id = id,
        name = name,
        category = category,
        baseScore = score,
        aliases = aliases,
        groceryCategory = section,
    )
