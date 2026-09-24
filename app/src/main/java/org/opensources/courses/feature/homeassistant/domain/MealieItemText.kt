package org.opensources.courses.feature.homeassistant.domain

import org.opensources.courses.feature.shopping.domain.ItemEntryParser
import java.util.Locale

/**
 * Text of the items of a Mealie shopping list, as Home Assistant exposes them: Mealie's display of
 * the article, quantity and unit first, then the food and its note ("250 grammes Pâtes",
 * "1 gousse ail", "½ cuillère à café sel", "graines de sésame ou selon le goût").
 *
 * Reading uses the same rules as the add field ([ItemEntryParser]), after turning Mealie's fractions
 * ("1/2", "1 ½") into decimals: "250 grammes Pâtes" is 250 g of "Pâtes", "1 mangue" is one "mangue".
 * A text without a leading quantity tells none. A linking word left dangling at the end by Mealie
 * ("crevettes décortiquées de") is dropped. Writing puts the quantity first the same way ("500 g
 * Pâtes", "2 Pain"), so what the app writes reads back as the same article.
 */
object MealieItemText {
    fun parse(
        text: String,
        local: HaItemContent? = null,
    ): HaItemContent {
        val clean = text.trim().replace(WHITESPACE, " ")
        return local?.takeIf { wroteFor(it, clean) } ?: read(clean)
    }

    /**
     * What this app wrote for the linked article reads back as that article, even with a unit typed
     * freely ("2 boîte Pâtes"). Not for an article whose name holds a quantity itself: it is a Mealie
     * text stored whole before Mealie was supported ("250 grammes Pâtes"), to be read now.
     */
    private fun wroteFor(
        local: HaItemContent,
        text: String,
    ): Boolean =
        comparable(format(local.name, local.quantity ?: 1.0, local.unit, Locale.ROOT)) == comparable(text) &&
            read(local.name).quantity == null

    private fun read(text: String): HaItemContent {
        val entry = ItemEntryParser.parse(withDecimalQuantity(text))
        return HaItemContent(withoutDanglingLink(entry.name).ifEmpty { text }, entry.quantity, entry.unit)
    }

    /** A quantity of 1 without unit is not written: "Pain" rather than "1 Pain". */
    fun format(
        name: String,
        quantity: Double,
        unit: String?,
        locale: Locale,
    ): String = ItemDescriptionCodec.encode(quantity, unit, locale)?.let { "$it $name" } ?: name

    /** Both decimal separators, so that phones in different languages recognise each other's texts. */
    private fun comparable(text: String): String = text.trim().replace(WHITESPACE, " ").replace(',', '.')

    private fun withDecimalQuantity(text: String): String {
        MIXED_FRACTION.find(text)?.let { match ->
            val (whole, numerator, denominator) = match.destructured
            return decimal(whole.toDouble(), numerator, denominator)?.let { text.replaceRange(match.range, it) } ?: text
        }
        SIMPLE_FRACTION.find(text)?.let { match ->
            val (numerator, denominator) = match.destructured
            return decimal(0.0, numerator, denominator)?.let { text.replaceRange(match.range, it) } ?: text
        }
        VULGAR_FRACTION.find(text)?.let { match ->
            val whole = match.groupValues[1].toDoubleOrNull() ?: 0.0
            val fraction = VULGAR_VALUES.getValue(match.groupValues[2].single())
            return text.replaceRange(match.range, written(whole + fraction))
        }
        return text
    }

    private fun decimal(
        whole: Double,
        numerator: String,
        denominator: String,
    ): String? {
        val divisor = denominator.toDouble().takeIf { it > 0.0 } ?: return null
        return written(whole + numerator.toDouble() / divisor)
    }

    /** At most three decimals, dot separated, as [ItemEntryParser] reads them. */
    private fun written(value: Double): String = String.format(Locale.ROOT, "%.3f", value).trimEnd('0').trimEnd('.')

    private fun withoutDanglingLink(name: String): String {
        var words = name.split(' ')
        while (words.size > 1 && words.last().lowercase(Locale.ROOT).trimEnd('\'', '’') in DANGLING_LINKS) words = words.dropLast(1)
        return words.joinToString(" ")
    }

    private val WHITESPACE = Regex("\\s+")
    private val MIXED_FRACTION = Regex("""^(\d+)\s+(\d+)\s*/\s*(\d+)(?=\s)""")
    private val SIMPLE_FRACTION = Regex("""^(\d+)\s*/\s*(\d+)(?=\s)""")
    private val VULGAR_VALUES =
        mapOf(
            '¼' to 0.25, '½' to 0.5, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3, '⅕' to 0.2, '⅖' to 0.4, '⅗' to 0.6,
            '⅘' to 0.8, '⅙' to 1.0 / 6, '⅚' to 5.0 / 6, '⅛' to 0.125, '⅜' to 0.375, '⅝' to 0.625, '⅞' to 0.875,
        )
    private val VULGAR_FRACTION = Regex("""^(\d+)?\s*([${VULGAR_VALUES.keys.joinToString("")}])""")

    /** Words that only link a measure or a food to what follows, in the six languages of the app. */
    private val DANGLING_LINKS = setOf("de", "d", "du", "des", "of", "von", "vom", "del", "di", "da", "do", "dos", "das", "degli", "della")
}
