package org.opensources.courses.feature.catalog.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {
    @Test
    fun `removes accents and case`() {
        assertEquals("lait demi ecreme", TextNormalizer.normalize("Lait Demi-Écrémé"))
    }

    @Test
    fun `expands ligatures`() {
        assertEquals("oeufs", TextNormalizer.normalize("Œufs"))
    }

    @Test
    fun `turns punctuation into single spaces`() {
        assertEquals("lait d amande", TextNormalizer.normalize("  Lait d'amande !! "))
    }

    @Test
    fun `handles the letters of every supported language`() {
        assertEquals("weissbrot kase", TextNormalizer.normalize("Weißbrot Käse"))
        assertEquals("pina limoes macas", TextNormalizer.normalize("Piña limões maçãs"))
        assertEquals("caffe d orzo", TextNormalizer.normalize("Caffè d’orzo"))
        assertEquals("istanbul", TextNormalizer.normalize("ISTANBUL"))
    }

    @Test
    fun `splits words`() {
        assertEquals(listOf("pommes", "de", "terre"), TextNormalizer.words("pommes de terre"))
    }
}
