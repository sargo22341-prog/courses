package org.opensources.courses.feature.language.domain

import kotlinx.coroutines.flow.StateFlow

/**
 * Language the application is displayed in. Until a language is chosen, it is the device language
 * when supported ([AppLanguage.resolve]); a chosen language is kept by Android and also appears in the
 * application's page of the Android settings, where it can be changed too.
 */
interface AppLanguageRepository {
    val language: StateFlow<AppLanguage>

    /** True when a language was set for this application rather than following the device. */
    fun hasChosenLanguage(): Boolean

    /** Applies [language] to every screen. Choosing the language already displayed changes nothing. */
    fun setLanguage(language: AppLanguage)

    /** Reads the language again: it may have been changed in the Android settings meanwhile. */
    fun refresh()
}
