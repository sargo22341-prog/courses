package org.opensources.courses.core.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * The real Android Keystore, with a DataStore of its own: the token saved by the app is never
 * touched. The key is shared with the app, and only ever created, never removed.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreSecretStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = File(context.cacheDir, "secrets-test-${UUID.randomUUID()}.preferences_pb")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { file }
    private val store = KeystoreSecretStore(dataStore)

    @After
    fun cleanUp() {
        scope.cancel()
        file.delete()
    }

    @Test
    fun aWrittenSecretIsReadBack() =
        runTest {
            store.write(NAME, "token-value")

            assertEquals("token-value", store.read(NAME))
        }

    @Test
    fun anUnreadableValueIsRemovedAndReadAsAbsent() =
        runTest {
            for (stored in listOf("no-separator", "not*base64:still*not", "AAAA:AAAA", "a:b:c")) {
                dataStore.edit { it[stringPreferencesKey(NAME)] = stored }

                assertNull(stored, store.read(NAME))
                assertNull(stored, dataStore.data.first()[stringPreferencesKey(NAME)])
            }
        }

    private companion object {
        const val NAME = "test_secret"
    }
}
