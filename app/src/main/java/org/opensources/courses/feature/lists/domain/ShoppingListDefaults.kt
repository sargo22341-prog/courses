package org.opensources.courses.feature.lists.domain

import org.opensources.courses.feature.language.domain.AppLanguage

object ShoppingListDefaults {
    /**
     * Name of the list created automatically at first launch, in the app language. It becomes user
     * data (renamable, stored in Room, sent to Home Assistant) rather than interface text, so it is
     * chosen here once and never translated afterwards.
     */
    fun defaultListName(language: AppLanguage): String =
        when (language) {
            AppLanguage.FRENCH -> "Courses"
            AppLanguage.ENGLISH -> "Groceries"
            AppLanguage.GERMAN -> "Einkaufsliste"
            AppLanguage.SPANISH -> "Compra"
            AppLanguage.ITALIAN -> "Spesa"
            AppLanguage.PORTUGUESE -> "Compras"
        }
}
