package org.opensources.courses.feature.homeassistant.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.opensources.courses.feature.homeassistant.domain.HaListMode
import org.opensources.courses.feature.homeassistant.domain.HaSaveResult
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantConfig
import org.opensources.courses.testing.FakeSecretStore
import java.io.File

class HaConfigRepositoryImplTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val secrets = FakeSecretStore()
    private val repository by lazy {
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "home_assistant.preferences_pb") }
        HaConfigRepositoryImpl(dataStore, secrets)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `an invalid address saves nothing, not even the token`() =
        runTest {
            assertEquals(HaSaveResult.INVALID_URL, repository.saveConnection("pas une url", "token"))

            assertEquals(HomeAssistantConfig.Default, repository.config.first())
            assertTrue(secrets.values.isEmpty())
        }

    @Test
    fun `the token goes to the secret store and each new token gets a new version`() =
        runTest {
            assertEquals(HaSaveResult.SAVED, repository.saveConnection("ha.nas.home:8123", "  secret-token "))
            val saved = repository.config.first()

            assertEquals("http://ha.nas.home:8123", saved.baseUrl)
            assertTrue(saved.hasToken)
            assertEquals(1, saved.tokenVersion)
            assertEquals("secret-token", secrets.values.values.single())

            // A blank token keeps the stored one for the same server, even over https: same version.
            assertEquals(HaSaveResult.SAVED, repository.saveConnection("https://ha.nas.home", ""))
            assertEquals(1, repository.config.first().tokenVersion)

            repository.saveConnection("https://ha.nas.home", "new-token")
            assertEquals(2, repository.config.first().tokenVersion)
        }

    @Test
    fun `forgetting erases the token and every setting`() =
        runTest {
            repository.saveConnection("https://ha.nas.home", "secret-token")
            repository.setEnabled(true)
            repository.setListMode(HaListMode.ALL_LISTS)
            repository.setListsSetupDone()

            repository.forgetConnection()

            assertEquals(HomeAssistantConfig.Default.copy(tokenVersion = 1), repository.config.first())
            assertNull(repository.credentials())
            assertNull(repository.credentialsFor("https://ha.nas.home", typedToken = ""))
            assertTrue(secrets.values.isEmpty())
        }

    @Test
    fun `a token saved after forgetting the connection is a new token version`() =
        runTest {
            repository.saveConnection("https://ha.nas.home", "secret-token")
            repository.forgetConnection()

            repository.saveConnection("https://ha.nas.home", "other-token")

            assertEquals(2, repository.config.first().tokenVersion)
        }

    @Test
    fun `the stored token is never tied to another server`() =
        runTest {
            repository.saveConnection("https://ha.nas.home", "secret-token")
            repository.setEnabled(true)

            assertEquals(HaSaveResult.TOKEN_REQUIRED, repository.saveConnection("http://ha-exemple.duckdns.org", ""))
            assertNull(repository.credentialsFor("http://ha-exemple.duckdns.org", typedToken = ""))
            assertEquals("https://ha.nas.home", repository.config.first().baseUrl)
            assertEquals("https://ha.nas.home", repository.credentials()?.baseUrl)

            // Same server, other scheme or port: the stored token is still used.
            assertEquals("secret-token", repository.credentialsFor("http://ha.nas.home:8123", typedToken = "")?.token)
            // A token typed for the other server is used and saved with it.
            assertEquals("other-token", repository.credentialsFor("http://ha-exemple.duckdns.org", "other-token")?.token)
            assertEquals(HaSaveResult.SAVED, repository.saveConnection("http://ha-exemple.duckdns.org", "other-token"))
            assertEquals("other-token", repository.credentials()?.token)
        }

    @Test
    fun `a token that can no longer be read leaves Home Assistant not configured`() =
        runTest {
            repository.saveConnection("https://ha.nas.home", "secret-token")
            repository.setEnabled(true)
            // What the Keystore store does with a token it cannot decrypt.
            secrets.values.clear()

            assertNull(repository.credentials())

            val config = repository.config.first()
            assertFalse(config.hasToken)
            assertFalse(config.isConfigured)
        }
}
