package org.opensources.courses.feature.language.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.opensources.courses.feature.language.domain.AppLanguageRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class LanguageDataModule {
    @Binds
    abstract fun bindAppLanguageRepository(repository: LocaleManagerAppLanguageRepository): AppLanguageRepository
}
