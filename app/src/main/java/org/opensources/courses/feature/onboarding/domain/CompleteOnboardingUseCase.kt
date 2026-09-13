package org.opensources.courses.feature.onboarding.domain

import org.opensources.courses.feature.lists.domain.ShoppingList
import org.opensources.courses.feature.lists.domain.ShoppingListRepository
import org.opensources.courses.feature.settings.domain.AppPreferencesRepository
import javax.inject.Inject

/** First launch: create the default list (named [defaultListName]) and never show the welcome again. */
class CompleteOnboardingUseCase
    @Inject
    constructor(
        private val lists: ShoppingListRepository,
        private val preferences: AppPreferencesRepository,
    ) {
        suspend operator fun invoke(defaultListName: String): ShoppingList {
            val list = lists.ensureDefaultList(defaultListName)
            preferences.setOnboardingCompleted()
            return list
        }
    }
