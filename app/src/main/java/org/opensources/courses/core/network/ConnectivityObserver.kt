package org.opensources.courses.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import org.opensources.courses.core.common.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exposes whether Android has a default network. A network only becomes the default when it is meant
 * to carry Internet traffic (`NET_CAPABILITY_INTERNET`), but Internet does not have to answer there:
 * validation (`NET_CAPABILITY_VALIDATED`) is deliberately not required, so a home Wi-Fi whose Internet
 * access is down still counts and Home Assistant on the local network is still tried. Whether a
 * server answers is only known from the request itself.
 */
interface ConnectivityObserver {
    val isOnline: StateFlow<Boolean>
}

@Singleton
class AndroidConnectivityObserver
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ) : ConnectivityObserver {
        private val manager = context.getSystemService(ConnectivityManager::class.java)

        override val isOnline: StateFlow<Boolean> =
            callbackFlow {
                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onCapabilitiesChanged(
                            network: Network,
                            capabilities: NetworkCapabilities,
                        ) {
                            trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                        }

                        override fun onLost(network: Network) {
                            trySend(false)
                        }
                    }
                manager.registerDefaultNetworkCallback(callback)
                awaitClose { manager.unregisterNetworkCallback(callback) }
            }.distinctUntilChanged()
                .stateIn(scope, SharingStarted.Eagerly, currentlyOnline())

        private fun currentlyOnline(): Boolean =
            manager
                .getNetworkCapabilities(manager.activeNetwork)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class ConnectivityModule {
    @Binds
    abstract fun bindConnectivityObserver(observer: AndroidConnectivityObserver): ConnectivityObserver
}
