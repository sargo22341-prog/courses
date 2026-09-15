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
        val number = DecimalFormat("#.##", DecimalFormatSymbols.getInstance(locale)).format(quantity)
        return if (unit.isNullOrBlank()) number else "$number ${unit.trim()}"
    }

    /** Parses `1,5` or `1.5` whatever the language; null when the text is not a strictly positive number. */
    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }
}
