package org.opensources.courses.feature.homeassistant.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class HaItemFormatTest {
    private val localTodo = HaTodoList("todo.courses", "Courses", supportsDescription = true)
    private val plain = HaTodoList("todo.shopping_list", "Shopping list", supportsDescription = false)

    @Test
    fun `descriptions win, then Mealie is recognised by its integration`() {
        assertEquals(HaItemFormat.DESCRIPTION, HaItemFormat.of(localTodo, "local_todo"))
        assertEquals(HaItemFormat.DESCRIPTION, HaItemFormat.of(localTodo, HaItemFormat.MEALIE_INTEGRATION))
        assertEquals(HaItemFormat.MEALIE, HaItemFormat.of(plain, HaItemFormat.MEALIE_INTEGRATION))
        assertEquals(HaItemFormat.NAME_ONLY, HaItemFormat.of(plain, "shopping_list"))
        assertEquals(HaItemFormat.NAME_ONLY, HaItemFormat.of(plain, null))
    }

    @Test
    fun `each format reads the item its own way`() {
        val item = HaTodoItem("uid", "250 grammes Pâtes", completed = false, description = "2")

        assertEquals(HaItemContent("250 grammes Pâtes", 2.0, null), HaItemFormat.DESCRIPTION.read(item))
        assertEquals(HaItemContent("250 grammes Pâtes", null, null), HaItemFormat.NAME_ONLY.read(item))
        assertEquals(HaItemContent("Pâtes", 250.0, "g"), HaItemFormat.MEALIE.read(item))
    }

    @Test
    fun `only Mealie puts the quantity in the text, only descriptions hold it apart`() {
        assertEquals("500 g Pâtes", HaItemFormat.MEALIE.summary("Pâtes", 500.0, "g", Locale.FRENCH))
        assertEquals("Pâtes", HaItemFormat.DESCRIPTION.summary("Pâtes", 500.0, "g", Locale.FRENCH))
        assertEquals("Pâtes", HaItemFormat.NAME_ONLY.summary("Pâtes", 500.0, "g", Locale.FRENCH))

        assertEquals("500 g", HaItemFormat.DESCRIPTION.description(500.0, "g", Locale.FRENCH))
        assertNull(HaItemFormat.MEALIE.description(500.0, "g", Locale.FRENCH))
        assertNull(HaItemFormat.NAME_ONLY.description(500.0, "g", Locale.FRENCH))

        assertTrue(HaItemFormat.MEALIE.syncsQuantity)
        assertFalse(HaItemFormat.NAME_ONLY.syncsQuantity)
    }
}
