package org.opensources.courses.feature.homeassistant.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItemDescriptionCodecTest {
    @Test
    fun `single unit without description is not written`() {
        assertNull(ItemDescriptionCodec.encode(1.0, null))
    }

    @Test
    fun `quantity and unit round trip`() {
        assertEquals("1,5 kg", ItemDescriptionCodec.encode(1.5, "kg"))
        assertEquals(ItemQuantity(1.5, "kg"), ItemDescriptionCodec.decode("1,5 kg"))
        assertEquals(ItemQuantity(12.0, null), ItemDescriptionCodec.decode("12"))
    }

    @Test
    fun `free text description means quantity one`() {
        assertEquals(ItemQuantity(1.0, null), ItemDescriptionCodec.decode("marque bio"))
        assertEquals(ItemQuantity(1.0, null), ItemDescriptionCodec.decode(null))
    }

    @Test
    fun `home assistant urls are normalized`() {
        assertEquals("http://homeassistant.local:8123", HaUrlNormalizer.normalize(" homeassistant.local:8123/ "))
        assertEquals("https://ha.example.org", HaUrlNormalizer.normalize("https://ha.example.org"))
        assertNull(HaUrlNormalizer.normalize("ftp://ha"))
        assertNull(HaUrlNormalizer.normalize(""))
    }
}
