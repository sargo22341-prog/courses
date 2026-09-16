package org.opensources.courses.feature.shopping.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.opensources.courses.feature.language.domain.AppLanguage
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

class QuantityFormatterTest {
    @Test
    fun `formats without useless decimals`() {
        assertEquals("12", QuantityFormatter.format(12.0, null, AppLanguage.FRENCH.locale))
        assertEquals("1,5 kg", QuantityFormatter.format(1.5, " kg ", AppLanguage.FRENCH.locale))
    }

    @Test
    fun `decimal separator follows the language`() {
        assertEquals("1.5 kg", QuantityFormatter.format(1.5, "kg", AppLanguage.ENGLISH.locale))
        listOf(AppLanguage.GERMAN, AppLanguage.SPANISH, AppLanguage.ITALIAN, AppLanguage.PORTUGUESE).forEach {
            assertEquals("$it", "2,25", QuantityFormatter.format(2.25, null, it.locale))
        }
    }

    @Test
    fun `whole quantities are written exactly as the decimal pattern writes them`() {
        listOf(1.0, 10.0, 1_000.0, 123_456_789.0, 9_007_199_254_740_991.0, 1e20).forEach { quantity ->
            AppLanguage.entries.forEach { language ->
                val expected = DecimalFormat("#.##", DecimalFormatSymbols.getInstance(language.locale)).format(quantity)
                assertEquals("$quantity $language", expected, QuantityFormatter.format(quantity, null, language.locale))
            }
        }
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
