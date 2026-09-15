package org.opensources.courses.feature.homeassistant.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HaUrlNormalizerTest {
    @Test
    fun `custom local https domains are accepted`() {
        assertEquals("https://ha.nas.home", HaUrlNormalizer.normalize("https://ha.nas.home"))
        assertEquals("https://ha.nas.home:8123", HaUrlNormalizer.normalize(" https://ha.nas.home:8123/ "))
    }

    @Test
    fun `scheme is added when missing`() {
        assertEquals("http://homeassistant.local:8123", HaUrlNormalizer.normalize("homeassistant.local:8123/"))
    }

    @Test
    fun `text that is not an http address is rejected`() {
        assertNull(HaUrlNormalizer.normalize("   "))
        assertNull(HaUrlNormalizer.normalize("ftp://ha.nas.home"))
        assertNull(HaUrlNormalizer.normalize("https://"))
    }
}
