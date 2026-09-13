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
import okhttp3.OkHttpClient
import org.opensources.courses.feature.catalog.data.remote.OpenFoodFactsApi
import org.opensources.courses.feature.catalog.data.remote.OpenFoodFactsCatalogSource
import org.opensources.courses.feature.catalog.domain.CatalogRemoteSource
import org.opensources.courses.feature.catalog.domain.CatalogRepository
import org.opensources.courses.feature.catalog.domain.CatalogSyncStateStore
import retrofit2.Retrofit
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
    abstract fun bindCatalogRemoteSource(source: OpenFoodFactsCatalogSource): CatalogRemoteSource

    @Binds
    abstract fun bindCatalogSyncStateStore(store: DataStoreCatalogSyncStateStore): CatalogSyncStateStore

    companion object {
        @Provides
        @Singleton
        fun openFoodFactsApi(client: OkHttpClient): OpenFoodFactsApi =
            Retrofit
                .Builder()
                .baseUrl(OpenFoodFactsApi.BASE_URL)
                .client(client)
                .build()
                .create(OpenFoodFactsApi::class.java)

        @Provides
        @Singleton
        @CatalogDataStore
        fun catalogDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("catalog") }
    }
}
