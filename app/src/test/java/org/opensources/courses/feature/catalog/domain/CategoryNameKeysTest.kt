package org.opensources.courses.feature.catalog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryNameKeysTest {
    @Test
    fun `exact name comes first, then plural forms`() {
        assertEquals(listOf("tomate cerise", "tomates cerise", "tomates cerises"), CategoryNameKeys.of("tomate cerise"))
    }

    @Test
    fun `plural names are also looked up in the singular`() {
        assertEquals(listOf("tomates", "tomate"), CategoryNameKeys.of("tomates"))
        assertEquals(listOf("gateaux", "gateau"), CategoryNameKeys.of("gateaux"))
        assertEquals(listOf("gateau", "gateaux"), CategoryNameKeys.of("gateau"))
    }

    @Test
    fun `short words are not stripped and blank names give nothing`() {
        assertEquals("riz", CategoryNameKeys.of("riz").first())
        assertTrue("ri" !in CategoryNameKeys.of("riz"))
        assertEquals(emptyList<String>(), CategoryNameKeys.of(""))
    }
}
