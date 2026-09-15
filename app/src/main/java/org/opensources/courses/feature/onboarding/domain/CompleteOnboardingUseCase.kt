package org.opensources.courses.feature.onboarding.domain

import org.opensources.courses.feature.language.domain.AppLanguageRepository
import org.opensources.courses.feature.lists.domain.ShoppingList
import org.opensources.courses.feature.lists.domain.ShoppingListDefaults
import org.opensources.courses.feature.lists.domain.ShoppingListRepository
import org.opensources.courses.feature.settings.domain.AppPreferencesRepository
import javax.inject.Inject

/**
 * First launch: the language shown on the welcome screen (the device language, or the one picked
 * there) is kept, the default list is created with a name in that language, and the welcome screen
 * is never shown again.
 */
class CompleteOnboardingUseCase
    @Inject
    constructor(
        private val lists: ShoppingListRepository,
        private val preferences: AppPreferencesRepository,
        private val languages: AppLanguageRepository,
    ) {
        suspend operator fun invoke(): ShoppingList {
            val list = lists.ensureDefaultList(ShoppingListDefaults.defaultListName(languages.language.value))
            preferences.setLanguageConfirmed()
            preferences.setOnboardingCompleted()
            return list
        }
    }
