package org.opensources.courses.feature.catalog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.courses.feature.language.domain.AppLanguage

class CategoryNameKeysTest {
    private fun keys(
        name: String,
        language: AppLanguage = AppLanguage.FRENCH,
    ) = CategoryNameKeys.of(TextNormalizer.normalize(name), language)

    @Test
    fun `exact name comes first, then plural forms`() {
        assertEquals(listOf("tomate cerise", "tomates cerise", "tomates cerises"), keys("tomate cerise"))
    }

    @Test
    fun `plural names are also looked up in the singular`() {
        assertEquals(listOf("tomates", "tomate"), keys("tomates"))
        assertEquals(listOf("gateaux", "gateau"), keys("gateaux"))
        assertEquals(listOf("gateau", "gateaux"), keys("gateau"))
    }

    @Test
    fun `short words are not stripped and blank names give nothing`() {
        assertEquals("riz", keys("riz").first())
        assertTrue("ri" !in keys("riz"))
        assertEquals(emptyList<String>(), keys(""))
    }

    @Test
    fun `english singular and plural endings`() {
        val english = AppLanguage.ENGLISH
        assertTrue("tomatoes" in keys("Tomato", english))
        assertTrue("strawberry" in keys("Strawberries", english))
        assertTrue("strawberries" in keys("strawberry", english))
        assertTrue("peach" in keys("peaches", english))
        assertTrue("apples" in keys("apple", english))
        assertEquals(listOf("hummus"), keys("hummus", english).take(1))
    }

    @Test
    fun `german singular and plural endings, umlauts already removed`() {
        val german = AppLanguage.GERMAN
        assertTrue("tomaten" in keys("Tomate", german))
        assertTrue("tomate" in keys("Tomaten", german))
        assertTrue("brote" in keys("Brot", german))
        assertTrue("eier" in keys("Ei", german))
        assertTrue("apfel" in keys("Äpfel", german))
    }

    @Test
    fun `spanish singular and plural endings`() {
        val spanish = AppLanguage.SPANISH
        assertTrue("manzanas" in keys("manzana", spanish))
        assertTrue("limon" in keys("limones", spanish))
        assertTrue("limones" in keys("limón", spanish))
        assertTrue("nueces" in keys("nuez", spanish))
        assertTrue("leche" in keys("leches", spanish))
    }

    @Test
    fun `italian singular and plural endings`() {
        val italian = AppLanguage.ITALIAN
        assertTrue("pomodori" in keys("Pomodoro", italian))
        assertTrue("mela" in keys("Mele", italian))
        assertTrue("albicocche" in keys("albicocca", italian))
        assertEquals(listOf("yogurt"), keys("yogurt", italian))
    }

    @Test
    fun `portuguese singular and plural endings`() {
        val portuguese = AppLanguage.PORTUGUESE
        assertTrue("limoes" in keys("limão", portuguese))
        assertTrue("limao" in keys("limões", portuguese))
        assertTrue("macas" in keys("maçã", portuguese))
        assertTrue("pastel" in keys("pastéis", portuguese))
    }
}
