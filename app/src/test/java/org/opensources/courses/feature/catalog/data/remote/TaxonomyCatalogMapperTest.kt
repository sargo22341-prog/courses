package org.opensources.courses.feature.catalog.data.remote

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.courses.feature.catalog.domain.GroceryCategory

class TaxonomyCatalogMapperTest {
    private val mapper = TaxonomyCatalogMapper()

    private val entries =
        mapOf(
            "en:dairies" to TaxonomyEntryDto(name = mapOf("fr" to "Produits laitiers")),
            "en:milks" to TaxonomyEntryDto(name = mapOf("fr" to "Laits", "en" to "Milks"), parents = listOf("en:dairies")),
            "en:semi-skimmed-milks" to TaxonomyEntryDto(name = mapOf("fr" to "Laits demi-écrémés"), parents = listOf("en:milks")),
            "en:english-only" to TaxonomyEntryDto(name = mapOf("en" to "Only English"), parents = listOf("en:dairies")),
            "fr:comte" to TaxonomyEntryDto(name = mapOf("fr" to "Comté"), protectedNameType = JsonPrimitive("pdo")),
            "en:honeys-from-jura" to TaxonomyEntryDto(name = mapOf("fr" to "Miels du Jura"), origins = JsonPrimitive("en:jura")),
            "en:baby-milk-2" to TaxonomyEntryDto(name = mapOf("fr" to "Laits 2ème âge"), parents = listOf("en:milks")),
            "en:too-long" to TaxonomyEntryDto(name = mapOf("fr" to "Laits entiers pasteurisés de montagne bio"), parents = listOf("en:milks")),
            "fr:laits" to TaxonomyEntryDto(name = mapOf("fr" to "laits"), parents = listOf("en:semi-skimmed-milks")),
        )

    private val products = mapper.map(entries).associateBy { it.id }

    @Test
    fun `keeps short french names`() {
        assertTrue("en:milks" in products)
        assertTrue("en:semi-skimmed-milks" in products)
        assertEquals("Laits demi-écrémés", products.getValue("en:semi-skimmed-milks").name)
    }

    @Test
    fun `drops entries without french name, protected, origin, digits or too long`() {
        listOf("en:english-only", "fr:comte", "en:honeys-from-jura", "en:baby-milk-2", "en:too-long").forEach {
            assertFalse("$it should be filtered", it in products)
        }
    }

    @Test
    fun `category is the shelf just below the root`() {
        assertEquals("Produits laitiers", products.getValue("en:milks").category)
        assertEquals("Laits", products.getValue("en:semi-skimmed-milks").category)
    }

    @Test
    fun `parent and base score follow the hierarchy`() {
        val milks = products.getValue("en:milks")
        val semiSkimmed = products.getValue("en:semi-skimmed-milks")
        assertEquals("en:milks", semiSkimmed.parentId)
        assertTrue(milks.baseScore > semiSkimmed.baseScore)
    }

    @Test
    fun `normalized duplicates keep the most generic entry`() {
        assertFalse("fr:laits" in products)
    }

    @Test
    fun `shop section is read on the taxonomy chain`() {
        assertEquals(GroceryCategory.DAIRY_EGGS, products.getValue("en:dairies").groceryCategory)
        assertEquals(GroceryCategory.DAIRY_EGGS, products.getValue("en:semi-skimmed-milks").groceryCategory)
    }

    @Test
    fun `the most specific known section wins and unknown chains have none`() {
        val mapped =
            mapper
                .map(
                    mapOf(
                        "en:plant-based-foods-and-beverages" to TaxonomyEntryDto(name = mapOf("fr" to "Aliments végétaux")),
                        "en:breads" to TaxonomyEntryDto(name = mapOf("fr" to "Pains"), parents = listOf("en:plant-based-foods-and-beverages")),
                        "en:baguettes" to TaxonomyEntryDto(name = mapOf("fr" to "Baguettes"), parents = listOf("en:breads")),
                        "en:food-additives" to TaxonomyEntryDto(name = mapOf("fr" to "Additifs alimentaires")),
                    ),
                ).associateBy { it.id }

        assertEquals(GroceryCategory.BAKERY, mapped.getValue("en:baguettes").groceryCategory)
        assertEquals(GroceryCategory.SAVORY_GROCERY, mapped.getValue("en:plant-based-foods-and-beverages").groceryCategory)
        assertNull(mapped.getValue("en:food-additives").groceryCategory)
    }
}
