package org.opensources.courses.feature.shopping.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.opensources.courses.feature.catalog.domain.ProductSuggestion
import org.opensources.courses.feature.language.domain.AppLanguage

class ExactSuggestionFinderTest {
    private val bread = ProductSuggestion("seed:pain", "Pain", null)
    private val breads = ProductSuggestion("off:pains", "Pains", null)
    private val chocolateBread = ProductSuggestion("seed:pain-choc", "Pain au chocolat", null)

    @Test
    fun `the typed name is found whatever its case and accents`() {
        assertEquals(bread, ExactSuggestionFinder.find(listOf(chocolateBread, bread), "PAÏN", AppLanguage.FRENCH))
    }

    @Test
    fun `a plural typed with a quantity finds the singular product`() {
        assertEquals(bread, ExactSuggestionFinder.find(listOf(chocolateBread, bread), "pains", AppLanguage.FRENCH))
    }

    @Test
    fun `the exact spelling wins over a plural form`() {
        assertEquals(breads, ExactSuggestionFinder.find(listOf(bread, breads), "Pains", AppLanguage.FRENCH))
    }

    @Test
    fun `a longer product is not the typed name`() {
        assertNull(ExactSuggestionFinder.find(listOf(chocolateBread), "pain", AppLanguage.FRENCH))
        assertNull(ExactSuggestionFinder.find(listOf(bread), "  ", AppLanguage.FRENCH))
    }
}
