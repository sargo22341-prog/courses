package org.opensources.courses.feature.shopping.domain

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object QuantityFormatter {
    private val symbols = DecimalFormatSymbols(Locale.FRANCE)

    /** `2`, `1,5 kg`, `12` — decimals only when needed. */
    fun format(
        quantity: Double,
        unit: String?,
    ): String {
        val number = DecimalFormat("#.##", symbols).format(quantity)
        return if (unit.isNullOrBlank()) number else "$number ${unit.trim()}"
    }

    /** Parses `1,5` or `1.5`; null when the text is not a strictly positive number. */
    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }
}
