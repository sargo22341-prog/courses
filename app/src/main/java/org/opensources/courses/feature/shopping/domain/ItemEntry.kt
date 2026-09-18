package org.opensources.courses.feature.shopping.domain

/**
 * What was typed in the add field, split into the product [name] and the quantity typed with it
 * ("2 pain", "500 g de pâtes"). [quantity] is null when none was typed: adding then follows the
 * usual rules (one more of a product already waiting).
 */
data class ItemEntry(
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
)
