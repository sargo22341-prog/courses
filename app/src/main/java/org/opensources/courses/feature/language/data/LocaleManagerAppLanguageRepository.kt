package org.opensources.courses.feature.language.data

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.opensources.courses.feature.language.domain.AppLanguage
import org.opensources.courses.feature.language.domain.AppLanguageRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-app language of Android ([LocaleManager]): no preference of our own to keep in sync, Android
 * persists the choice, recreates the screens in the new language and offers it in its settings.
 */
@Singleton
class LocaleManagerAppLanguageRepository
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : AppLanguageRepository {
        private val localeManager = context.getSystemService(LocaleManager::class.java)
        private val current = MutableStateFlow(read())
        override val language: StateFlow<AppLanguage> = current.asStateFlow()

        override fun hasChosenLanguage(): Boolean = !localeManager.applicationLocales.isEmpty

        override fun setLanguage(language: AppLanguage) {
            if (language == current.value) return
            localeManager.applicationLocales = LocaleList.forLanguageTags(language.tag)
            current.value = language
        }

        override fun refresh() {
            current.value = read()
        }

        private fun read(): AppLanguage {
            val locales = localeManager.applicationLocales.takeUnless { it.isEmpty } ?: localeManager.systemLocales
            return AppLanguage.resolve((0 until locales.size()).map { locales[it].toLanguageTag() })
        }
    }
