package org.opensources.courses.feature.shopping.presentation.components

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import org.opensources.courses.R
import org.opensources.courses.feature.catalog.domain.GroceryCategory
import java.util.EnumMap

/**
 * Name, pictogram and accent of a shop section. Accents are fixed per category, like the
 * categories themselves (green for vegetables, blue for dairy…), which is why they are not theme
 * roles; [CategoryHeader] adapts them to the current theme. Each keeps a contrast of at least 4.5:1
 * as text on the light background.
 */
internal class CategoryStyle(
    @param:StringRes val label: Int,
    @param:StringRes val icon: Int,
    val accent: Color,
)

/** Built once: every section header asks for its style at each composition. */
private val styles = EnumMap(GroceryCategory.entries.associateWith { it.createStyle() })

internal fun GroceryCategory.style(): CategoryStyle = styles.getValue(this)

private fun GroceryCategory.createStyle(): CategoryStyle =
    when (this) {
        GroceryCategory.FRUITS_VEGETABLES -> CategoryStyle(R.string.category_fruits_vegetables, R.string.category_icon_fruits_vegetables, Color(0xFF3F7A2A))
        GroceryCategory.BAKERY -> CategoryStyle(R.string.category_bakery, R.string.category_icon_bakery, Color(0xFF8A5A1C))
        GroceryCategory.DAIRY_EGGS -> CategoryStyle(R.string.category_dairy_eggs, R.string.category_icon_dairy_eggs, Color(0xFF2F6A9E))
        GroceryCategory.MEAT_FISH -> CategoryStyle(R.string.category_meat_fish, R.string.category_icon_meat_fish, Color(0xFFA33A32))
        GroceryCategory.DELI -> CategoryStyle(R.string.category_deli, R.string.category_icon_deli, Color(0xFFA2521A))
        GroceryCategory.SAVORY_GROCERY -> CategoryStyle(R.string.category_savory_grocery, R.string.category_icon_savory_grocery, Color(0xFF6F6526))
        GroceryCategory.SWEET_GROCERY -> CategoryStyle(R.string.category_sweet_grocery, R.string.category_icon_sweet_grocery, Color(0xFFA03F6E))
        GroceryCategory.FROZEN -> CategoryStyle(R.string.category_frozen, R.string.category_icon_frozen, Color(0xFF1F7482))
        GroceryCategory.DRINKS -> CategoryStyle(R.string.category_drinks, R.string.category_icon_drinks, Color(0xFF5E4FA6))
        GroceryCategory.HYGIENE_HOUSEHOLD ->
            CategoryStyle(R.string.category_hygiene_household, R.string.category_icon_hygiene_household, Color(0xFF2E7A66))
        GroceryCategory.BABY_PETS -> CategoryStyle(R.string.category_baby_pets, R.string.category_icon_baby_pets, Color(0xFF8A5294))
        GroceryCategory.OTHER -> CategoryStyle(R.string.category_other, R.string.category_icon_other, Color(0xFF6C675F))
    }
