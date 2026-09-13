package org.opensources.courses.feature.catalog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatcherTest {
    private val matcher = FuzzyMatcher()

    @Test
    fun `tolerates an adjacent transposition`() {
        assertTrue(matcher.matches("lati", "lait"))
    }

    @Test
    fun `tolerates an extra letter in a long word`() {
        assertTrue(matcher.matches("tomatte", "tomates cerises"))
    }

    @Test
    fun `matches every word of a multi-word query`() {
        assertTrue(matcher.matches("pomme de tere", "pommes de terre"))
    }

    @Test
    fun `stays strict for short queries`() {
        assertFalse(matcher.matches("riz", "roti"))
    }

    @Test
    fun `rejects unrelated words`() {
        assertFalse(matcher.matches("chocolat", "tomates"))
    }

    @Test
    fun `prefix distance ignores the rest of the word`() {
        assertEquals(0, matcher.prefixDistance("tom", "tomates"))
        assertEquals(1, matcher.prefixDistance("tomatos", "tomates"))
    }
}
