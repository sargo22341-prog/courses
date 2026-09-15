package org.opensources.courses.feature.catalog.domain

enum class CatalogSource {
    /** Curated list bundled with the app: available offline from the very first launch. */
    SEED,

    /** Imported from the OpenFoodFacts categories taxonomy. */
    OPEN_FOOD_FACTS,

    /** Typed by the user when nothing matched. */
    CUSTOM,
}

/**
 * @property parentId more generic product this one is a variant of (`Lait entier` → `Lait`).
 */
data class CatalogProduct(
    val id: String,
    val name: String,
    val category: String?,
    val brand: String?,
    val parentId: String?,
    val source: CatalogSource,
)

/** A product that may match a query, with everything the ranking needs. */
data class ProductCandidate(
    val product: CatalogProduct,
    val aliases: List<String>,
    val baseScore: Int,
    val useCount: Int,
    val lastUsedAt: Long?,
)

data class ProductSuggestion(
    val productId: String,
    val name: String,
    val category: String?,
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
    val parentId: String?,
    val baseScore: Int,
    val aliases: List<String> = emptyList(),
    val groceryCategory: GroceryCategory? = null,
)
