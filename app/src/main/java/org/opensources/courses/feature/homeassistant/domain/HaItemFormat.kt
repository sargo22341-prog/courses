package org.opensources.courses.feature.homeassistant.domain

import java.util.Locale

/**
 * What a Home Assistant item says once read in the format of its list.
 *
 * @property quantity null when the item does not tell it: the local quantity (and unit) stays.
 */
data class HaItemContent(
    val name: String,
    val quantity: Double?,
    val unit: String?,
)

/** How the items of a Home Assistant list carry the name and quantity of an article. */
enum class HaItemFormat {
    /** Local To-do and any list accepting descriptions: the quantity is written in the description. */
    DESCRIPTION,

    /** Legacy Shopping list and any list without description: only the name, the quantity stays local. */
    NAME_ONLY,

    /**
     * Mealie: no description, the item text is Mealie's display of the article, quantity and unit
     * first ("250 grammes Pâtes", [MealieItemText]). Mealie turns an item whose text is changed into
     * a plain note (it loses its food and amount), so the text is only sent when the user changed the
     * article.
     */
    MEALIE,
    ;

    /** Whether Home Assistant holds the quantity of the items, so that it is compared and synchronised. */
    val syncsQuantity: Boolean get() = this != NAME_ONLY

    val sendsDescription: Boolean get() = this == DESCRIPTION

    /**
     * [local] is the article this item is linked to, if any: an item holding exactly what this app
     * would write for it reads back as that article, whatever the unit typed.
     */
    fun read(
        item: HaTodoItem,
        local: HaItemContent? = null,
    ): HaItemContent =
        when (this) {
            DESCRIPTION -> ItemDescriptionCodec.decode(item.description).let { HaItemContent(item.summary, it.quantity, it.unit) }
            NAME_ONLY -> HaItemContent(item.summary, quantity = null, unit = null)
            MEALIE -> MealieItemText.parse(item.summary, local)
        }

    /** Text of the item for an article, written with the decimal separator of [locale]. */
    fun summary(
        name: String,
        quantity: Double,
        unit: String?,
        locale: Locale,
    ): String = if (this == MEALIE) MealieItemText.format(name, quantity, unit, locale) else name

    /** Description of the item for this quantity; null when the list has none or it stays empty. */
    fun description(
        quantity: Double,
        unit: String?,
        locale: Locale,
    ): String? = if (sendsDescription) ItemDescriptionCodec.encode(quantity, unit, locale) else null

    companion object {
        /** Integration of the Mealie to-do lists in the Home Assistant entity registry. */
        const val MEALIE_INTEGRATION = "mealie"

        /** [integration] of the list's entity, when known: descriptions win, then Mealie is recognised. */
        fun of(
            list: HaTodoList,
            integration: String?,
        ): HaItemFormat =
            when {
                list.supportsDescription -> DESCRIPTION
                integration == MEALIE_INTEGRATION -> MEALIE
                else -> NAME_ONLY
            }
    }
}
