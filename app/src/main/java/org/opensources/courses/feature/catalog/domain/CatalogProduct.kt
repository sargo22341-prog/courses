package org.opensources.courses.feature.catalog.domain

/** Declared from the most to the least reliable source to file a product under a shop section. */
enum class CatalogSource {
    /** Curated list bundled with the app: available offline from the very first launch. */
    SEED,

    /** Generated from the OpenFoodFacts categories taxonomy and bundled too, but far less precise. */
    OPEN_FOOD_FACTS,

    /** Typed by the user when nothing matched. */
    CUSTOM,
}

data class CatalogProduct(
    val id: String,
    val name: String,
    val category: String?,
    val source: CatalogSource,
)

/**
 * A product that may match a query, with everything the ranking needs.
 *
 * @property normalizedName [TextNormalizer] form of the product name, as stored with it.
 * @property normalizedAliases [TextNormalizer] forms of its aliases, as stored with them.
 */
data class ProductCandidate(
    val product: CatalogProduct,
    val normalizedName: String,
    val normalizedAliases: List<String>,
    val baseScore: Int,
    val useCount: Int,
    val lastUsedAt: Long?,
)

/** @property normalizedName [TextNormalizer] form of [name], to recognise the suggestion matching a typed text. */
data class ProductSuggestion(
    val productId: String,
    val name: String,
    val category: String?,
    val normalizedName: String = TextNormalizer.normalize(name),
)

/** A stored product, as much as linking a list item to it needs. */
data class CatalogProductRef(
    val id: String,
    val normalizedName: String,
    val source: CatalogSource,
)

/**
 * A product produced by an import, before it is stored.
 *
 * @property category display name of its category in the source (`Produits laitiers`, `Laits`).
 * @property groceryCategory shop section used to sort a list, when the source allows to tell it.
 */
data class CatalogImportProduct(
    val id: String,
    val name: String,
    val category: String?,
    val baseScore: Int,
    val aliases: List<String> = emptyList(),
    val groceryCategory: GroceryCategory? = null,
)
