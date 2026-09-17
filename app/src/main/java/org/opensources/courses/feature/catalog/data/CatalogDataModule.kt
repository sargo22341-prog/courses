package org.opensources.courses.feature.catalog.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.opensources.courses.feature.catalog.data.seed.AssetSeedCatalogSource
import org.opensources.courses.feature.catalog.data.taxonomy.AssetTaxonomyCatalogSource
import org.opensources.courses.feature.catalog.domain.BundledCatalogSource
import org.opensources.courses.feature.catalog.domain.CatalogImportStateStore
import org.opensources.courses.feature.catalog.domain.CatalogRepository
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CatalogDataStore

@Module
@InstallIn(SingletonComponent::class)
abstract class CatalogDataModule {
    @Binds
    abstract fun bindCatalogRepository(repository: CatalogRepositoryImpl): CatalogRepository

    @Binds
    abstract fun bindCatalogImportStateStore(store: DataStoreCatalogImportStateStore): CatalogImportStateStore

    companion object {
        /** The curated catalog first: where both know a product, its section is the one kept. */
        @Provides
        @Singleton
        fun bundledCatalogSources(
            seed: AssetSeedCatalogSource,
            taxonomy: AssetTaxonomyCatalogSource,
        ): List<BundledCatalogSource> = listOf(seed, taxonomy)

        @Provides
        @Singleton
        @CatalogDataStore
        fun catalogDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("catalog") }
    }
}
