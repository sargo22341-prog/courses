package org.opensources.courses.feature.catalog.domain

/**
 * Shop section a product belongs to. The declaration order is the order sections are shown in,
 * roughly the walk through a supermarket; [OTHER] gathers everything the catalog does not know.
 */
enum class GroceryCategory {
    FRUITS_VEGETABLES,
    BAKERY,
    DAIRY_EGGS,
    MEAT_FISH,
    DELI,
    SAVORY_GROCERY,
    SWEET_GROCERY,
    FROZEN,
    DRINKS,
    HYGIENE_HOUSEHOLD,
    BABY_PETS,
    OTHER,
}
