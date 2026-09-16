package org.opensources.courses.feature.shopping.domain

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object QuantityFormatter {
    /** `2`, `12`, `1,5 kg` in French, `1.5 kg` in English — decimals only when needed. */
    fun format(
        quantity: Double,
        unit: String?,
        locale: Locale,
    ): String {
        val number =
            if (quantity % 1.0 == 0.0 && quantity < MAX_EXACT_WHOLE) {
                // Whole quantities, by far the most common, need no pattern to be parsed.
                quantity.toLong().toString()
            } else {
                DecimalFormat("#.##", DecimalFormatSymbols.getInstance(locale)).format(quantity)
            }
        return if (unit.isNullOrBlank()) number else "$number ${unit.trim()}"
    }

    /** Parses `1,5` or `1.5` whatever the language; null when the text is not a strictly positive number. */
    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }

    /** Below 2^53, every whole double is exactly a Long; larger ones keep the formatter's output. */
    private const val MAX_EXACT_WHOLE = 9_007_199_254_740_992.0
}
