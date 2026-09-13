package org.opensources.courses.feature.shopping.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuantityFormatterTest {
    @Test
    fun `formats without useless decimals`() {
        assertEquals("12", QuantityFormatter.format(12.0, null))
        assertEquals("1,5 kg", QuantityFormatter.format(1.5, " kg "))
    }

    @Test
    fun `parses comma and dot decimals`() {
        assertEquals(1.5, QuantityFormatter.parse("1,5")!!, 0.0)
        assertEquals(2.25, QuantityFormatter.parse("2.25")!!, 0.0)
    }

    @Test
    fun `rejects zero, negative and invalid quantities`() {
        assertNull(QuantityFormatter.parse("0"))
        assertNull(QuantityFormatter.parse("-1"))
        assertNull(QuantityFormatter.parse("beaucoup"))
    }
}
