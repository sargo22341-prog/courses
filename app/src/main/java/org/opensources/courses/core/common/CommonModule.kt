package org.opensources.courses.core.common

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CommonModule {
    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    /**
     * Last resort only: work launched here handles its own errors. One it missed must not kill the
     * app, whose local data stay usable; it is logged so that it is still seen.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default +
                CoroutineExceptionHandler { _, exception ->
                    // Tokens only travel in request headers and in the WebSocket auth message, never in an exception.
                    Log.e(APPLICATION_SCOPE_TAG, "Unhandled error in background work", exception)
                },
        )

    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemDefaultZone()

    private const val APPLICATION_SCOPE_TAG = "ApplicationScope"
}
