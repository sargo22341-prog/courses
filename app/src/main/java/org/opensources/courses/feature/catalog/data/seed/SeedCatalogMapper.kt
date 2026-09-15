package org.opensources.courses.feature.catalog.data.seed

import kotlinx.serialization.Serializable
import org.opensources.courses.feature.catalog.domain.CatalogImportProduct
import org.opensources.courses.feature.catalog.domain.GroceryCategory
import org.opensources.courses.feature.catalog.domain.TextNormalizer
import org.opensources.courses.feature.language.domain.AppLanguage

/** Every text is given in every [AppLanguage], keyed by its tag (guarded by tests). */
@Serializable
data class SeedCatalogDto(
    val version: Int,
    val categories: List<SeedCategoryDto>,
)

/** @property category name of a [GroceryCategory] constant. */
@Serializable
data class SeedCategoryDto(
    val category: String,
    val name: Map<String, String>,
    val products: List<SeedProductDto>,
)

/** @property variants names of more specific products, each in every language. */
@Serializable
data class SeedProductDto(
    val name: Map<String, String>,
    val aliases: Map<String, List<String>> = emptyMap(),
    val variants: List<Map<String, String>> = emptyList(),
)

object SeedCatalogMapper {
    private const val PRODUCT_SCORE = 8
    private const val VARIANT_SCORE = 6

    /**
     * @throws IllegalArgumentException when a section is not a [GroceryCategory], or
     * NoSuchElementException when a text misses a language (both guarded by tests).
     */
    fun map(
        seed: SeedCatalogDto,
        language: AppLanguage,
    ): List<CatalogImportProduct> =
        seed.categories
            .flatMap { category ->
                val groceryCategory = GroceryCategory.valueOf(category.category)
                val categoryName = category.name.getValue(language.tag)
                category.products.flatMap { product ->
                    val parentId = idFor(product.name)
                    val aliases = product.aliases[language.tag].orEmpty()
                    listOf(
                        CatalogImportProduct(parentId, product.name.getValue(language.tag), categoryName, null, PRODUCT_SCORE, aliases, groceryCategory),
                    ) +
                        product.variants.map { variant ->
                            CatalogImportProduct(
                                idFor(variant),
                                variant.getValue(language.tag),
                                categoryName,
                                parentId,
                                VARIANT_SCORE,
                                groceryCategory = groceryCategory,
                            )
                        }
                }
            }.distinctBy { it.id }

    /**
     * Built from the French name, the original language of the catalog, so ids are the same in every
     * language and did not change when languages were added: usage statistics and list items stay
     * linked to their product whatever the language.
     */
    fun idFor(names: Map<String, String>): String = "seed:" + TextNormalizer.normalize(names.getValue(AppLanguage.FRENCH.tag)).replace(' ', '-')
}
