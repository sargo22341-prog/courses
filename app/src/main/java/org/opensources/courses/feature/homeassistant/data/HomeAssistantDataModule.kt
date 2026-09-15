package org.opensources.courses.feature.homeassistant.data

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
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.opensources.courses.core.sync.RemoteSyncEngine
import org.opensources.courses.feature.homeassistant.data.remote.HomeAssistantApi
import org.opensources.courses.feature.homeassistant.data.remote.HomeAssistantClient
import org.opensources.courses.feature.homeassistant.data.remote.HomeAssistantWebSocketClient
import org.opensources.courses.feature.homeassistant.data.sync.HomeAssistantSyncEngine
import org.opensources.courses.feature.homeassistant.data.sync.RoomSyncLocalStore
import org.opensources.courses.feature.homeassistant.data.sync.SyncLocalStore
import org.opensources.courses.feature.homeassistant.domain.HaConfigRepository
import org.opensources.courses.feature.homeassistant.domain.HaListLinkRepository
import org.opensources.courses.feature.homeassistant.domain.HaLiveUpdates
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantGateway
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class HomeAssistantDataStore

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeAssistantDataModule {
    @Binds
    abstract fun bindConfigRepository(repository: HaConfigRepositoryImpl): HaConfigRepository

    @Binds
    abstract fun bindGateway(client: HomeAssistantClient): HomeAssistantGateway

    @Binds
    abstract fun bindLiveUpdates(client: HomeAssistantWebSocketClient): HaLiveUpdates

    @Binds
    abstract fun bindListLinkRepository(repository: HaListLinkRepositoryImpl): HaListLinkRepository

    @Binds
    abstract fun bindSyncLocalStore(store: RoomSyncLocalStore): SyncLocalStore

    @Binds
    abstract fun bindRemoteSyncEngine(engine: HomeAssistantSyncEngine): RemoteSyncEngine

    companion object {
        @Provides
        @Singleton
        fun homeAssistantApi(
            client: OkHttpClient,
            json: Json,
        ): HomeAssistantApi =
            Retrofit
                .Builder()
                // Placeholder only: every call passes the user's absolute URL.
                .baseUrl("http://localhost/")
                .client(client.newBuilder().readTimeout(HA_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS).build())
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(HomeAssistantApi::class.java)

        @Provides
        @Singleton
        @HomeAssistantDataStore
        fun homeAssistantDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("home_assistant") }

        private const val HA_READ_TIMEOUT_SECONDS = 15L
    }
}
