package org.opensources.courses.feature.shopping.domain

/**
 * Finds a quantity typed with the product name, so that it needs no editing afterwards:
 * - before the name: "2 pain", "2x pain", "x2 pain", "500g de pâtes", "1 kg d'oranges", "1,5 L lait";
 * - after the name, only with a unit or a multiplication sign: "pâtes 500 g", "lait x2", "lait 2x".
 *
 * A bare number after the name is part of it ("Pastis 51", "Oméga 3"). The name must start with a
 * letter, so "100 % jus" stays as typed. Only metric and imperial weights and volumes are units:
 * "2 bouteilles" is two of a product named "bouteilles", never a unit guessed from a word.
 */
object ItemEntryParser {
    fun parse(text: String): ItemEntry {
        val clean = text.trim().replace(WHITESPACE, " ")
        return measuredFirst(clean) ?: countedFirst(clean) ?: quantityLast(clean) ?: ItemEntry(clean)
    }

    private fun measuredFirst(text: String): ItemEntry? =
        MEASURED_FIRST.matchEntire(text)?.destructured?.let { (number, unit, name) -> entry(name, number, unit) }

    private fun countedFirst(text: String): ItemEntry? =
        COUNTED_FIRST.matchEntire(text)?.destructured?.let { (number, name) -> entry(name, number, unit = "") }

    private fun quantityLast(text: String): ItemEntry? {
        val groups = QUANTITY_LAST.matchEntire(text)?.groupValues ?: return null
        val number = groups[2].ifEmpty { groups[3] }.ifEmpty { groups[4] }
        return entry(groups[1], number, groups[5])
    }

    private fun entry(
        name: String,
        number: String,
        unit: String,
    ): ItemEntry? {
        val quantity = QuantityFormatter.parse(number)?.takeIf { it <= MAX_QUANTITY } ?: return null
        return ItemEntry(name.trim(), quantity, unit.takeIf { it.isNotEmpty() }?.let(::canonicalUnit))
    }

    private fun canonicalUnit(typed: String): String = UNITS.getValue(typed.lowercase())

    /** Every spelling accepted, in the six languages of the app, and the unit it stands for. */
    private val UNITS: Map<String, String> =
        buildMap {
            fun unit(
                canonical: String,
                vararg spellings: String,
            ) = (spellings.toList() + canonical.lowercase()).forEach { put(it, canonical) }
            unit("g", "gr", "grs", "gramme", "grammes", "gram", "grams", "gramm", "gramo", "gramos", "grammo", "grammi", "grama", "gramas")
            unit("kg", "kgs", "kilo", "kilos", "kilogramme", "kilogrammes", "kilogram", "kilograms", "kilogramm", "kilogramo", "kilogramos")
            unit("mg")
            unit("L", "lt", "litre", "litres", "liter", "liters", "litro", "litros", "litri")
            unit("cl")
            unit("dl")
            unit("ml")
            unit("lb", "lbs")
            unit("oz")
        }

    // Longest first: "grammes" must not stop at "g".
    private val UNIT = UNITS.keys.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
    private const val NUMBER = """\d+(?:[.,]\d+)?"""
    private const val TIMES = "[x×*]"

    /** "de", "d'", "of"… between a measure and the product; German needs none. */
    private const val LINK = """(?:(?:de|du|des|of|di|da|do|del)\s+|d['’]\s*)?"""
    private const val NAME = """(\p{L}.*)"""

    private val MEASURED_FIRST = Regex("""($NUMBER)\s*($UNIT)\.?\s+$LINK$NAME""", RegexOption.IGNORE_CASE)
    private val COUNTED_FIRST = Regex("""(?:$TIMES\s*)?($NUMBER)(?:\s*$TIMES)?\s+$NAME""", RegexOption.IGNORE_CASE)
    private val QUANTITY_LAST =
        Regex("""(\p{L}.*?)\s+(?:$TIMES\s*($NUMBER)|($NUMBER)\s*$TIMES|($NUMBER)\s*($UNIT)\.?)""", RegexOption.IGNORE_CASE)

    private val WHITESPACE = Regex("\\s+")

    /** Beyond, the number is more likely part of the name than a quantity. */
    private const val MAX_QUANTITY = 10_000.0
}
