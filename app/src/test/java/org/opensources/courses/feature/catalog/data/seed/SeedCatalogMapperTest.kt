package org.opensources.courses.feature.catalog.data.seed

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.courses.feature.catalog.domain.GroceryCategory
import java.io.File

/** Reads the real bundled asset: an invalid section name would make the whole seed import fail. */
class SeedCatalogMapperTest {
    private val seed = Json.decodeFromString(SeedCatalogDto.serializer(), File("src/main/assets/catalog/seed_fr.json").readText())
    private val products = SeedCatalogMapper.map(seed).associateBy { it.name }

    @Test
    fun `every bundled product has a shop section`() {
        assertTrue(products.isNotEmpty())
        assertTrue(products.values.all { it.groceryCategory != null })
    }

    @Test
    fun `variants share the section of their product`() {
        assertEquals(GroceryCategory.DAIRY_EGGS, products.getValue("Lait entier").groceryCategory)
        assertEquals(GroceryCategory.FRUITS_VEGETABLES, products.getValue("Tomates cerises").groceryCategory)
        assertEquals(GroceryCategory.HYGIENE_HOUSEHOLD, products.getValue("Lessive").groceryCategory)
    }
}
