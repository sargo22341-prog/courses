package org.opensources.courses.feature.catalog.data.remote

import org.opensources.courses.feature.catalog.domain.GroceryCategory

/**
 * Shop section of an OpenFoodFacts category, read on its taxonomy chain (the category itself, then
 * its ancestors): the first known id wins, so a specific entry (`en:breads`) takes precedence over
 * a broad root (`en:plant-based-foods-and-beverages`). Every id exists in `categories.json`
 * (checked in September 2026); an unknown chain (additives, supplements…) has no section.
 */
internal object OpenFoodFactsGroceryCategories {
    fun categoryOf(chain: List<String>): GroceryCategory? = chain.firstNotNullOfOrNull(byId::get)

    private val byId: Map<String, GroceryCategory> =
        mapOf(
            GroceryCategory.FRUITS_VEGETABLES to
                listOf(
                    "en:fruits-and-vegetables-based-foods", "en:fruits", "en:vegetables", "en:fresh-vegetables", "en:fresh-fruits",
                    "en:dried-fruits", "en:nuts", "en:mushrooms", "en:potatoes", "en:aromatic-plants", "en:legumes-and-their-products",
                ),
            GroceryCategory.BAKERY to
                listOf("en:breads", "en:viennoiseries", "en:pastries", "en:brioches", "en:crepes-and-galettes", "en:sweet-pies", "en:pies", "en:cakes"),
            GroceryCategory.DAIRY_EGGS to
                listOf(
                    "en:dairies", "en:eggs", "en:eggs-and-their-products", "en:cheeses", "en:milks", "en:yogurts", "en:butters", "en:creams",
                    "en:dairy-desserts",
                ),
            GroceryCategory.MEAT_FISH to
                listOf(
                    "en:meats-and-their-products", "en:meats", "en:seafood", "en:fishes", "en:fish-and-meat-and-eggs", "en:poultries",
                    "en:prepared-meats", "en:meat-alternatives", "en:caviar-substitutes",
                ),
            GroceryCategory.DELI to
                listOf("en:meals", "en:sandwiches", "en:pizzas-pies-and-quiches", "en:terrines", "en:pastas-and-dumplings", "en:meal-kits", "en:breaded-products"),
            GroceryCategory.FROZEN to listOf("en:frozen-foods", "en:ice-creams-and-sorbets", "en:frozen-desserts"),
            GroceryCategory.DRINKS to
                listOf("en:beverages-and-beverages-preparations", "en:beverages", "en:alcoholic-beverages", "en:waters", "en:syrups"),
            GroceryCategory.SWEET_GROCERY to
                listOf(
                    "en:sweet-snacks", "en:biscuits-and-cakes", "en:confectioneries", "en:chocolates", "en:cocoa-and-its-products", "en:desserts",
                    "en:breakfasts", "en:bee-products", "en:sweeteners", "en:sweet-spreads", "en:jams", "en:fruit-preserves", "en:compotes",
                    "en:dessert-sauces", "en:breakfast-cereals", "en:sugars",
                ),
            GroceryCategory.SAVORY_GROCERY to
                listOf(
                    "en:salty-snacks", "en:appetizers", "en:condiments", "en:sauces", "en:canned-foods", "en:cereals-and-potatoes", "en:pastas",
                    "en:rices", "en:legumes", "en:fats", "en:dried-products", "en:cooking-helpers", "en:broths", "en:spreads", "en:chips-and-fries",
                    "en:snacks", "en:plant-based-foods", "en:plant-based-foods-and-beverages", "en:cereals-and-their-products", "en:flours",
                    "en:spices", "en:vinegars", "en:soups",
                ),
            GroceryCategory.BABY_PETS to listOf("en:baby-foods", "en:baby-milks"),
        ).flatMap { (category, ids) -> ids.map { it to category } }.toMap()
}
