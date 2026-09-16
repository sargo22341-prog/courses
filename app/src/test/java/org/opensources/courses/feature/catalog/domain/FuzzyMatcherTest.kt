package org.opensources.courses.feature.catalog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatcherTest {
    private val matcher = FuzzyMatcher()

    private fun matches(
        query: String,
        text: String,
    ) = matcher.matches(SearchQuery(query), TextNormalizer.words(text))

    @Test
    fun `tolerates an adjacent transposition`() {
        assertTrue(matches("lati", "lait"))
    }

    @Test
    fun `tolerates an extra letter in a long word`() {
        assertTrue(matches("tomatte", "tomates cerises"))
    }

    @Test
    fun `matches every word of a multi-word query`() {
        assertTrue(matches("pomme de tere", "pommes de terre"))
    }

    @Test
    fun `stays strict for short queries`() {
        assertFalse(matches("riz", "roti"))
    }

    @Test
    fun `rejects unrelated words`() {
        assertFalse(matches("chocolat", "tomates"))
    }

    @Test
    fun `prefix distance ignores the rest of the word`() {
        assertEquals(0, matcher.prefixDistance("tom", "tomates"))
        assertEquals(1, matcher.prefixDistance("tomatos", "tomates"))
    }

    @Test
    fun `prefix distance counts every kind of edit`() {
        assertEquals(1, matcher.prefixDistance("lati", "lait"))
        assertEquals(1, matcher.prefixDistance("pian", "pain"))
        assertEquals(1, matcher.prefixDistance("lat", "lait"))
        assertEquals(1, matcher.prefixDistance("laiit", "lait"))
        assertEquals(2, matcher.prefixDistance("baguete", "bgauette"))
        assertEquals(3, matcher.prefixDistance("abc", "xyz"))
        assertEquals(4, matcher.prefixDistance("pain", ""))
    }

    @Test
    fun `prefix distance stops as soon as the limit is exceeded`() {
        assertTrue(matcher.prefixDistance("chocolat", "tomates", limit = 1) > 1)
    }
}
