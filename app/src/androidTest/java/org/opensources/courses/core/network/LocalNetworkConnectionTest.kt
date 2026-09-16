package org.opensources.courses.core.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.courses.feature.homeassistant.data.HomeAssistantDataModule
import org.opensources.courses.feature.homeassistant.data.remote.HomeAssistantClient
import org.opensources.courses.feature.homeassistant.domain.HaCredentials
import org.opensources.courses.feature.homeassistant.domain.HaErrorKind
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantException

/**
 * Real connection from the app process to a server on the local network, through the production
 * HTTP stack (same OkHttp client, network security config and permissions as the app).
 *
 * Needs a reachable server answering `GET /api/`, so it only runs when given instrumentation
 * arguments; skipped otherwise:
 * ```
 * adb shell am instrument -w -e class org.opensources.courses.core.network.LocalNetworkConnectionTest \
 *   -e haLocalUrl http://192.168.0.10:8123 -e expectReachable true \
 *   org.opensources.courses.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 * `expectReachable false` checks the failure when ACCESS_LOCAL_NETWORK has been revoked
 * (`adb shell pm revoke org.opensources.courses android.permission.ACCESS_LOCAL_NETWORK`).
 */
@RunWith(AndroidJUnit4::class)
class LocalNetworkConnectionTest {
    private val arguments = InstrumentationRegistry.getArguments()

    @Test
    fun connectsToLocalHomeAssistantAccordingToLocalNetworkPermission() =
        runTest {
            val url = arguments.getString(URL_ARGUMENT)
            assumeTrue("No $URL_ARGUMENT instrumentation argument", url != null)
            val expectReachable = arguments.getString(EXPECT_ARGUMENT, "true").toBoolean()
            val json = NetworkModule.json()
            val client = HomeAssistantClient(HomeAssistantDataModule.homeAssistantApi({ NetworkModule.okHttpClient() }, json))
            val credentials = HaCredentials(url!!, TEST_TOKEN)

            try {
                client.testConnection(credentials)
                if (!expectReachable) fail("Local server reached although it was expected to be blocked")
            } catch (exception: HomeAssistantException) {
                if (expectReachable) fail("Local server not reached: ${exception.kind} (${exception.cause})")
                assertEquals(HaErrorKind.UNREACHABLE, exception.kind)
            }
        }

    private companion object {
        const val URL_ARGUMENT = "haLocalUrl"
        const val EXPECT_ARGUMENT = "expectReachable"
        const val TEST_TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJ0ZXN0In0.c2lnbmF0dXJl"
    }
}
