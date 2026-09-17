package org.opensources.courses.feature.catalog.data.taxonomy

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.courses.feature.catalog.domain.GroceryCategory
import org.opensources.courses.feature.catalog.domain.TextNormalizer
import org.opensources.courses.feature.language.domain.AppLanguage
import java.io.File

/**
 * Reads the real generated assets. They are written by `scripts/generate-catalog.py`, outside any
 * build: these checks are what keeps them in step with the code that imports them, and what would
 * catch a generation run that produced something the application cannot use.
 */
class AssetTaxonomyCatalogTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val byLanguage =
        AppLanguage.entries.associateWith { language ->
            json.decodeFromString(
                TaxonomyCatalogDto.serializer(),
                File("src/main/assets/${AssetTaxonomyCatalogSource.assetPath(language)}").readText(),
            )
        }

    @Test
    fun `every language has a file, of the version known without reading it`() {
        // Otherwise a changed catalog would never be imported, or be imported at every start.
        byLanguage.forEach { (language, catalog) ->
            assertEquals("$language", AssetTaxonomyCatalogSource.VERSION, catalog.version)
            assertEquals(language.tag, catalog.language)
            assertTrue("$language", catalog.products.size > 500)
        }
    }

    @Test
    fun `every name is short enough to be typed on a shopping list`() {
        byLanguage.forEach { (language, catalog) ->
            catalog.products.forEach { product ->
                val name = product.name
                assertTrue("$language $name", name.isNotBlank())
                assertTrue("$language $name", name.length <= MAX_NAME_LENGTH)
                assertTrue("$language $name", name.none { it.isDigit() })
                // Words as they are written, not as the search splits them: `Laits demi-écrémés` counts for two.
                assertTrue("$language $name", name.split(' ').count { it.isNotBlank() } <= MAX_WORDS)
            }
        }
    }

    @Test
    fun `no two products of a language share an id or a name`() {
        byLanguage.forEach { (language, catalog) ->
            val duplicateIds = catalog.products.groupBy { it.id }.filterValues { it.size > 1 }.keys
            assertEquals("$language", emptySet<String>(), duplicateIds)
            // A drift between the generator and TextNormalizer would show up as two rows for one name.
            val duplicateNames = catalog.products.groupBy { TextNormalizer.normalize(it.name) }.filterValues { it.size > 1 }.keys
            assertEquals("$language", emptySet<String>(), duplicateNames)
        }
    }

    @Test
    fun `ids are taxonomy ids, the same in every language`() {
        byLanguage.forEach { (language, catalog) ->
            catalog.products.forEach { assertTrue("$language ${it.id}", ':' in it.id) }
        }
        val french = byLanguage.getValue(AppLanguage.FRENCH).products.associateBy { it.id }
        val english = byLanguage.getValue(AppLanguage.ENGLISH).products.associateBy { it.id }
        assertEquals("Laits", french.getValue("en:milks").name)
        assertEquals("Milks", english.getValue("en:milks").name)
    }

    @Test
    fun `an alias never repeats the name of its product and is never empty`() {
        byLanguage.forEach { (language, catalog) ->
            catalog.products.forEach { product ->
                val normalized = product.aliases.map(TextNormalizer::normalize)
                assertTrue("$language ${product.name}", normalized.none { it.isBlank() })
                assertTrue("$language ${product.name}", TextNormalizer.normalize(product.name) !in normalized)
                assertEquals("$language ${product.name}", normalized.size, normalized.distinct().size)
            }
        }
    }

    @Test
    fun `the taxonomy synonyms the published JSON drops are imported as aliases`() {
        val french = byLanguage.getValue(AppLanguage.FRENCH).products.associateBy { it.id }

        assertEquals(listOf("lait"), french.getValue("en:milks").aliases)
        assertTrue("patates" in french.getValue("en:potatoes").aliases)
        assertTrue("alimentation infantile" in french.getValue("en:baby-foods").aliases)
        // Worth the generation step only if it covers a sizeable part of the catalog.
        assertTrue(byLanguage.getValue(AppLanguage.FRENCH).products.count { it.aliases.isNotEmpty() } > 1_000)
    }

    @Test
    fun `almost every product is filed under a shop section`() {
        byLanguage.forEach { (language, catalog) ->
            val placed = catalog.products.count { it.section != null }
            assertTrue("$language: $placed / ${catalog.products.size}", placed > catalog.products.size * 95 / 100)
        }
        // The taxonomy only covers food: these two sections come from the curated catalog alone.
        val sections = byLanguage.values.flatMap { catalog -> catalog.products.mapNotNull { it.section } }.toSet()
        assertTrue(GroceryCategory.HYGIENE_HOUSEHOLD !in sections)
        assertTrue(GroceryCategory.OTHER !in sections)
    }

    @Test
    fun `a product carries everything the import needs`() {
        val milk = byLanguage.getValue(AppLanguage.FRENCH).products.single { it.id == "en:milks" }.toDomain()

        assertEquals("en:milks", milk.id)
        assertEquals("Laits", milk.name)
        assertEquals(GroceryCategory.DAIRY_EGGS, milk.groceryCategory)
        assertEquals(listOf("lait"), milk.aliases)
        assertTrue(milk.baseScore > 0)
        assertTrue(milk.category != null)
    }

    private companion object {
        const val MAX_NAME_LENGTH = 40
        const val MAX_WORDS = 4
    }
}
