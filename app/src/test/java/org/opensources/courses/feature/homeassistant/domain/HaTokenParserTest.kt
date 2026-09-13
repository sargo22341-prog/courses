package org.opensources.courses.feature.homeassistant.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HaTokenParserTest {
    private val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJhYmMxMjMifQ.dGVzdC1zaWduYXR1cmVfLQ"

    @Test
    fun `raw token from the Home Assistant QR code is accepted`() {
        assertEquals(token, HaTokenParser.parse(token))
    }

    @Test
    fun `surrounding spaces and a bearer prefix are removed`() {
        assertEquals(token, HaTokenParser.parse("  Bearer $token\n"))
    }

    @Test
    fun `other QR codes are rejected`() {
        assertNull(HaTokenParser.parse("WIFI:S:maison;T:WPA;P:secret;;"))
        assertNull(HaTokenParser.parse("http://homeassistant.local:8123"))
        assertNull(HaTokenParser.parse("abc.def.ghi"))
        assertNull(HaTokenParser.parse(""))
    }
}
