package org.opensources.courses.feature.catalog.data.remote

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.courses.feature.catalog.domain.GroceryCategory
import org.opensources.courses.feature.language.domain.AppLanguage

class TaxonomyCatalogMapperTest {
    private fun entry(
        name: String?,
        vararg parents: String,
        isProtectedName: Boolean = false,
        hasOrigins: Boolean = false,
    ) = TaxonomyEntryDto(TaxonomyName(name), parents.toList(), isProtectedName, hasOrigins)

    private val entries =
        mapOf(
            "en:dairies" to entry("Produits laitiers"),
            "en:milks" to entry("Laits", "en:dairies"),
            "en:semi-skimmed-milks" to entry("Laits demi-écrémés", "en:milks"),
            "en:english-only" to entry(null, "en:dairies"),
            "fr:comte" to entry("Comté", isProtectedName = true),
            "en:honeys-from-jura" to entry("Miels du Jura", hasOrigins = true),
            "en:baby-milk-2" to entry("Laits 2ème âge", "en:milks"),
            "en:too-long" to entry("Laits entiers pasteurisés de montagne bio", "en:milks"),
            "fr:laits" to entry("laits", "en:semi-skimmed-milks"),
        )

    private val products = TaxonomyCatalogMapper.map(entries).associateBy { it.id }

    @Test
    fun `keeps short names`() {
        assertTrue("en:milks" in products)
        assertTrue("en:semi-skimmed-milks" in products)
        assertEquals("Laits demi-écrémés", products.getValue("en:semi-skimmed-milks").name)
    }

    @Test
    fun `drops entries without a name, protected, origin, digits or too long`() {
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
    fun `generic entries get a higher base score`() {
        assertTrue(products.getValue("en:milks").baseScore > products.getValue("en:semi-skimmed-milks").baseScore)
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
            TaxonomyCatalogMapper
                .map(
                    mapOf(
                        "en:plant-based-foods-and-beverages" to entry("Aliments végétaux"),
                        "en:breads" to entry("Pains", "en:plant-based-foods-and-beverages"),
                        "en:baguettes" to entry("Baguettes", "en:breads"),
                        "en:food-additives" to entry("Additifs alimentaires"),
                    ),
                ).associateBy { it.id }

        assertEquals(GroceryCategory.BAKERY, mapped.getValue("en:baguettes").groceryCategory)
        assertEquals(GroceryCategory.SAVORY_GROCERY, mapped.getValue("en:plant-based-foods-and-beverages").groceryCategory)
        assertNull(mapped.getValue("en:food-additives").groceryCategory)
    }

    @Test
    fun `names and categories are read in the requested language, ids stay the taxonomy ids`() {
        val english = decode(AppLanguage.ENGLISH)
        val french = decode(AppLanguage.FRENCH)

        assertEquals(setOf("en:dairies", "en:milks", "en:english-only"), english.keys)
        assertEquals("Milks", english.getValue("en:milks").name)
        assertEquals("Dairies", english.getValue("en:milks").category)
        assertEquals(GroceryCategory.DAIRY_EGGS, english.getValue("en:milks").groceryCategory)
        assertEquals(setOf("en:dairies", "en:milks", "en:null-origins"), french.keys)
        assertEquals("Laits", french.getValue("en:milks").name)
    }

    @Test
    fun `protected names and origins are recognised whatever their value, unknown fields are skipped`() {
        val entries = decodeEntries(AppLanguage.FRENCH)

        assertTrue(entries.getValue("fr:comte").isProtectedName)
        assertTrue(entries.getValue("en:honeys-from-jura").hasOrigins)
        assertFalse(entries.getValue("en:milks").isProtectedName)
        assertFalse(entries.getValue("en:milks").hasOrigins)
        assertFalse(entries.getValue("en:null-origins").hasOrigins)
        assertEquals(TaxonomyName.NONE, entries.getValue("en:no-name").name)
    }

    private fun decode(language: AppLanguage) = TaxonomyCatalogMapper.map(decodeEntries(language)).associateBy { it.id }

    private fun decodeEntries(language: AppLanguage): Map<String, TaxonomyEntryDto> =
        taxonomyJson(Json { ignoreUnknownKeys = true }, language)
            .decodeFromString(MapSerializer(String.serializer(), TaxonomyEntryDto.serializer()), TAXONOMY)

    private companion object {
        val TAXONOMY =
            """
            {
              "en:dairies": { "name": { "en": "Dairies", "fr": "Produits laitiers", "de": "Milchprodukte" }, "wikidata": { "en": "Q185217" } },
              "en:milks": { "name": { "fr": "Laits", "en": "Milks" }, "parents": ["en:dairies"], "children": ["en:whole-milks"] },
              "en:english-only": { "name": { "en": "Only English" }, "parents": ["en:dairies"] },
              "fr:comte": { "name": { "fr": "Comté" }, "protected_name_type": { "en": "pdo" } },
              "en:honeys-from-jura": { "name": { "fr": "Miels du Jura" }, "origins": { "en": "en:jura" } },
              "en:null-origins": { "name": { "fr": "Beurres" }, "origins": null },
              "en:no-name": { "parents": ["en:dairies"] }
            }
            """.trimIndent()
    }
}
