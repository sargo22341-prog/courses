package org.opensources.courses.feature.homeassistant.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class MealieItemTextTest {
    private fun read(
        text: String,
        local: HaItemContent? = null,
    ) = MealieItemText.parse(text, local)

    @Test
    fun `the items of a real Mealie list are read as article, quantity and unit`() {
        assertEquals(HaItemContent("Pâtes", 250.0, "g"), read("250 grammes Pâtes"))
        assertEquals(HaItemContent("mangue", 1.0, null), read("1 mangue"))
        assertEquals(HaItemContent("concombre", 200.0, "g"), read("200 grammes concombre"))
        assertEquals(HaItemContent("oignon rouge", 40.0, "g"), read("40 grammes oignon rouge"))
        assertEquals(HaItemContent("curry", 1.0, null), read("1 curry"))
        assertEquals(HaItemContent("gousse ail", 1.0, null), read("1 gousse ail"))
    }

    @Test
    fun `a text without leading quantity tells none`() {
        assertEquals(HaItemContent("graines de sésame ou selon le goût", null, null), read("graines de sésame ou selon le goût"))
        assertEquals(HaItemContent("Pain", null, null), read("Pain"))
    }

    @Test
    fun `a linking word left dangling by Mealie is dropped`() {
        assertEquals(HaItemContent("crevettes décortiquées", 200.0, "g"), read("200 grammes crevettes décortiquées de"))
        assertEquals(HaItemContent("Mehl", 1.0, "kg"), read("1 kg Mehl von"))
    }

    @Test
    fun `Mealie fractions become decimal quantities`() {
        assertEquals(HaItemContent("cuillère à café sel", 0.5, null), read("½ cuillère à café sel"))
        assertEquals(HaItemContent("farine", 1.5, "kg"), read("1 1/2 kg farine"))
        assertEquals(HaItemContent("lait", 0.25, "L"), read("1/4 L lait"))
        assertEquals(HaItemContent("citron", 1.5, null), read("1½ citron"))
    }

    @Test
    fun `a division by zero is not a quantity`() {
        assertEquals(HaItemContent("1/0 citron", null, null), read("1/0 citron"))
    }

    @Test
    fun `what the app writes reads back as the same article`() {
        listOf(
            HaItemContent("Pâtes", 500.0, "g"),
            HaItemContent("Pain", 2.0, null),
            HaItemContent("Pommes", 1.5, "kg"),
            HaItemContent("Lait", 1.0, "L"),
        ).forEach { article ->
            val text = MealieItemText.format(article.name, article.quantity ?: 1.0, article.unit, Locale.FRENCH)
            assertEquals(text, article, read(text))
        }
        assertEquals("1,5 kg Pommes", MealieItemText.format("Pommes", 1.5, "kg", Locale.FRENCH))
        assertEquals("Pain", MealieItemText.format("Pain", 1.0, null, Locale.FRENCH))
    }

    @Test
    fun `the text written for the linked article reads back as it, whatever its unit or name`() {
        val boxes = HaItemContent("Pâtes", 2.0, "boîte")
        assertEquals(boxes, read("2 boîte Pâtes", local = boxes))
        val soda = HaItemContent("7up", 2.0, null)
        assertEquals(soda, read("2 7up", local = soda))
        // Written by a phone in English: the other decimal separator.
        val apples = HaItemContent("Pommes", 1.5, "kg")
        assertEquals(apples, read("1.5 kg Pommes", local = apples))
    }

    @Test
    fun `a Mealie text stored whole before Mealie was supported is read now`() {
        assertEquals(HaItemContent("Pâtes", 250.0, "g"), read("250 grammes Pâtes", local = HaItemContent("250 grammes Pâtes", 1.0, null)))
        assertEquals(HaItemContent("mangue", 1.0, null), read("1 mangue", local = HaItemContent("1 mangue", 1.0, null)))
    }

    @Test
    fun `a text changed in Mealie is read again, not taken for the linked article`() {
        assertEquals(HaItemContent("Pâtes", 300.0, "g"), read("300 grammes Pâtes", local = HaItemContent("Pâtes", 250.0, "g")))
    }
}
