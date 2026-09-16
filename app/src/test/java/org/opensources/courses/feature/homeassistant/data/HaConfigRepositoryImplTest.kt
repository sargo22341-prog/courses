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
            assertFalse(repository.saveConnection("pas une url", "token"))

            assertEquals(HomeAssistantConfig.Default, repository.config.first())
            assertTrue(secrets.values.isEmpty())
        }

    @Test
    fun `the token goes to the secret store and each new token gets a new version`() =
        runTest {
            assertTrue(repository.saveConnection("ha.nas.home:8123", "  secret-token "))
            val saved = repository.config.first()

            assertEquals("http://ha.nas.home:8123", saved.baseUrl)
            assertTrue(saved.hasToken)
            assertEquals(1, saved.tokenVersion)
            assertEquals("secret-token", secrets.values.values.single())

            // A blank token keeps the stored one: same version.
            repository.saveConnection("https://ha.nas.home", "")
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

            assertEquals(HomeAssistantConfig.Default, repository.config.first())
            assertNull(repository.credentials())
            assertNull(repository.credentialsFor("https://ha.nas.home", typedToken = ""))
            assertTrue(secrets.values.isEmpty())
        }
}
