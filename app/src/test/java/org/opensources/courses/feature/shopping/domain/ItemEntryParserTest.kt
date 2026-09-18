package org.opensources.courses.feature.shopping.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ItemEntryParserTest {
    private fun parse(text: String) = ItemEntryParser.parse(text)

    @Test
    fun `a count before the name is the quantity`() {
        assertEquals(ItemEntry("pain", 2.0), parse("2 pain"))
        assertEquals(ItemEntry("Pains au chocolat", 12.0), parse("  12   Pains au chocolat "))
    }

    @Test
    fun `a multiplication sign before or after the count is accepted`() {
        assertEquals(ItemEntry("lait", 2.0), parse("2x lait"))
        assertEquals(ItemEntry("lait", 2.0), parse("2 x lait"))
        assertEquals(ItemEntry("lait", 3.0), parse("x3 lait"))
        assertEquals(ItemEntry("lait", 2.0), parse("2× lait"))
    }

    @Test
    fun `a measure before the name keeps its unit and drops the linking word`() {
        assertEquals(ItemEntry("pâtes", 500.0, "g"), parse("500g de pâtes"))
        assertEquals(ItemEntry("pâtes", 500.0, "g"), parse("500 g pâtes"))
        assertEquals(ItemEntry("oranges", 1.0, "kg"), parse("1 kg d'oranges"))
        assertEquals(ItemEntry("oranges", 1.0, "kg"), parse("1 kilo d’oranges"))
        assertEquals(ItemEntry("lait", 1.5, "L"), parse("1,5 L de lait"))
        assertEquals(ItemEntry("crème", 20.0, "cl"), parse("20cl crème"))
        assertEquals(ItemEntry("farine", 250.0, "g"), parse("250 grammes de farine"))
        assertEquals(ItemEntry("milk", 2.0, "L"), parse("2 liters of milk"))
        assertEquals(ItemEntry("Mehl", 500.0, "g"), parse("500 g Mehl"))
        assertEquals(ItemEntry("pasta", 500.0, "g"), parse("500 gr di pasta"))
    }

    @Test
    fun `a quantity after the name needs a unit or a multiplication sign`() {
        assertEquals(ItemEntry("pâtes", 500.0, "g"), parse("pâtes 500 g"))
        assertEquals(ItemEntry("Coca", 1.5, "L"), parse("Coca 1,5 L"))
        assertEquals(ItemEntry("lait", 2.0), parse("lait x2"))
        assertEquals(ItemEntry("lait", 2.0), parse("lait 2x"))
    }

    @Test
    fun `a bare number after the name is part of it`() {
        assertEquals(ItemEntry("Pastis 51"), parse("Pastis 51"))
        assertEquals(ItemEntry("Kronenbourg 1664"), parse("Kronenbourg 1664"))
        assertEquals(ItemEntry("Oméga 3"), parse("Oméga 3"))
    }

    @Test
    fun `a word starting like a unit is not taken for one`() {
        assertEquals(ItemEntry("gâteaux", 3.0), parse("3 gâteaux"))
        assertEquals(ItemEntry("laitues", 2.0), parse("2 laitues"))
        assertEquals(ItemEntry("gousses d'ail", 2.0), parse("2 gousses d'ail"))
        assertEquals(ItemEntry("xérès", 2.0), parse("2 xérès"))
    }

    @Test
    fun `text without a quantity is kept as typed`() {
        assertEquals(ItemEntry("Lait"), parse("  Lait  "))
        assertEquals(ItemEntry("100 % pur jus"), parse("100 % pur jus"))
        assertEquals(ItemEntry("7up"), parse("7up"))
        assertEquals(ItemEntry("2"), parse("2"))
        assertEquals(ItemEntry(""), parse("   "))
    }

    @Test
    fun `zero and huge numbers are not quantities`() {
        assertEquals(ItemEntry("0 pain"), parse("0 pain"))
        assertEquals(ItemEntry("20000 lieues"), parse("20000 lieues"))
    }
}
