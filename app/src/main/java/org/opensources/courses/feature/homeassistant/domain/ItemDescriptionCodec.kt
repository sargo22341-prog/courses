package org.opensources.courses.feature.homeassistant.domain

import org.opensources.courses.feature.shopping.domain.QuantityFormatter

data class ItemQuantity(
    val quantity: Double,
    val unit: String?,
)

/**
 * Home Assistant to-do items have no quantity: it is written in the item description (`2`,
 * `1,5 kg`) for lists that support descriptions. A quantity of 1 without unit clears the
 * description. A description that does not start with a number is read as quantity 1.
 */
object ItemDescriptionCodec {
    private val PATTERN = Regex("^\\s*(\\d+(?:[.,]\\d+)?)\\s*(.*?)\\s*$")

    fun encode(
        quantity: Double,
        unit: String?,
    ): String? = if (quantity == 1.0 && unit.isNullOrBlank()) null else QuantityFormatter.format(quantity, unit)

    fun decode(description: String?): ItemQuantity {
        val match = description?.let { PATTERN.matchEntire(it) } ?: return ItemQuantity(1.0, null)
        val quantity = QuantityFormatter.parse(match.groupValues[1]) ?: return ItemQuantity(1.0, null)
        return ItemQuantity(quantity, match.groupValues[2].takeIf { it.isNotBlank() })
    }
}
