package org.opensources.courses.feature.onboarding.domain

import kotlinx.coroutines.flow.first
import org.opensources.courses.feature.language.domain.AppLanguage
import org.opensources.courses.feature.language.domain.AppLanguageRepository
import org.opensources.courses.feature.settings.domain.AppPreferencesRepository
import javax.inject.Inject

/**
 * The app used to exist in French only. An install that went through the welcome screen before the
 * language choice existed stays in French, the language of its catalog and lists, instead of
 * switching to the device language on update; its language can then be changed in the settings.
 * Runs once: a language later reset in the Android settings is respected.
 */
class KeepFrenchForExistingInstallUseCase
    @Inject
    constructor(
        private val preferences: AppPreferencesRepository,
        private val languages: AppLanguageRepository,
    ) {
        suspend operator fun invoke() {
            val current = preferences.preferences.first()
            if (!current.onboardingCompleted || current.languageConfirmed) return
            if (!languages.hasChosenLanguage()) languages.setLanguage(AppLanguage.FRENCH)
            preferences.setLanguageConfirmed()
        }
    }
