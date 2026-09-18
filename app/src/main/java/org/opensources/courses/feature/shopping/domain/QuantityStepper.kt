package org.opensources.courses.feature.shopping.domain

import kotlin.math.roundToLong

/**
 * The "+" and "−" buttons of an item: one more of a counted product, a step that makes sense for a
 * measured one (100 g, half a kilo). A quantity never goes down to zero: deleting is a separate gesture.
 */
object QuantityStepper {
    fun increase(
        quantity: Double,
        unit: String?,
    ): Double = rounded(quantity + step(unit))

    /** Null when one step less would leave nothing. */
    fun decrease(
        quantity: Double,
        unit: String?,
    ): Double? = rounded(quantity - step(unit)).takeIf { it > 0 }

    fun step(unit: String?): Double =
        when (unit?.trim()?.lowercase()) {
            "g", "ml", "mg" -> 100.0
            "kg", "l" -> 0.5
            "cl" -> 10.0
            else -> 1.0
        }

    /** 0.1 + 0.2 must read 0.3: steps are kept to three decimals. */
    private fun rounded(value: Double): Double = (value * PRECISION).roundToLong() / PRECISION

    private const val PRECISION = 1_000.0
}

/** "−" is offered only while one step less still leaves something. */
val ShoppingItem.canDecreaseQuantity: Boolean get() = QuantityStepper.decrease(quantity, unit) != null
