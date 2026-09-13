package org.opensources.courses.feature.homeassistant.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class HaListNameAllocatorTest {
    @Test
    fun `free name is kept`() {
        assertEquals("Courses", HaListNameAllocator.uniqueName("Courses", listOf("BBQ", "Shopping list")))
    }

    @Test
    fun `taken name gets the first free number`() {
        assertEquals("Courses 2", HaListNameAllocator.uniqueName("Courses", listOf("Courses")))
        assertEquals("Courses 3", HaListNameAllocator.uniqueName("Courses", listOf("Courses", "Courses 2")))
    }

    @Test
    fun `names differing only by case or accents are the same name`() {
        assertEquals("Épicerie 2", HaListNameAllocator.uniqueName("Épicerie", listOf("epicerie")))
    }
}
