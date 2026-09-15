package org.opensources.courses.feature.catalog.data.seed

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.courses.feature.catalog.domain.GroceryCategory
import org.opensources.courses.feature.catalog.domain.TextNormalizer
import org.opensources.courses.feature.language.domain.AppLanguage
import java.io.File

/** Reads the real bundled asset: a missing translation or an invalid section would make the whole seed import fail. */
class SeedCatalogMapperTest {
    private val seed = Json.decodeFromString(SeedCatalogDto.serializer(), File("src/main/assets/catalog/seed.json").readText())
    private val byLanguage = AppLanguage.entries.associateWith { SeedCatalogMapper.map(seed, it) }

    @Test
    fun `every text exists in every language`() {
        val tags = AppLanguage.entries.map { it.tag }.toSet()
        seed.categories.forEach { category ->
            assertEquals(category.category, tags, category.name.keys)
            category.products.forEach { product ->
                assertEquals(product.name.toString(), tags, product.name.keys)
                assertTrue(product.name.toString(), (product.aliases.keys - tags).isEmpty())
                product.variants.forEach { assertEquals(it.toString(), tags, it.keys) }
            }
        }
    }

    @Test
    fun `every bundled product has a shop section`() {
        byLanguage.values.forEach { products ->
            assertTrue(products.isNotEmpty())
            assertTrue(products.all { it.groceryCategory != null })
        }
    }

    @Test
    fun `ids are the same in every language and did not change when languages were added`() {
        val frenchIds = byLanguage.getValue(AppLanguage.FRENCH).map { it.id }
        byLanguage.values.forEach { products -> assertEquals(frenchIds, products.map { it.id }) }
        val english = byLanguage.getValue(AppLanguage.ENGLISH).associateBy { it.id }
        assertEquals("Milk", english.getValue("seed:lait").name)
        assertEquals("Cherry tomatoes", english.getValue("seed:tomates-cerises").name)
        assertEquals("Eggs", english.getValue("seed:oeufs").name)
    }

    @Test
    fun `no two products share a name in the same language`() {
        byLanguage.forEach { (language, products) ->
            val duplicates = products.groupBy { TextNormalizer.normalize(it.name) }.filterValues { it.size > 1 }.keys
            assertEquals("$language", emptySet<String>(), duplicates)
        }
    }

    @Test
    fun `variants share the section of their product`() {
        val french = byLanguage.getValue(AppLanguage.FRENCH).associateBy { it.name }
        assertEquals(GroceryCategory.DAIRY_EGGS, french.getValue("Lait entier").groceryCategory)
        assertEquals(GroceryCategory.FRUITS_VEGETABLES, french.getValue("Tomates cerises").groceryCategory)
        assertEquals(GroceryCategory.HYGIENE_HOUSEHOLD, french.getValue("Lessive").groceryCategory)
        val german = byLanguage.getValue(AppLanguage.GERMAN).associateBy { it.name }
        assertEquals(GroceryCategory.DAIRY_EGGS, german.getValue("Vollmilch").groceryCategory)
        assertEquals("Milchprodukte", german.getValue("Vollmilch").category)
    }

    @Test
    fun `aliases follow the language`() {
        val italian = byLanguage.getValue(AppLanguage.ITALIAN).associateBy { it.id }
        assertEquals(listOf("pomodoro"), italian.getValue("seed:tomates").aliases)
        assertEquals(emptyList<String>(), byLanguage.getValue(AppLanguage.ITALIAN).single { it.id == "seed:poires" }.aliases)
    }
}
