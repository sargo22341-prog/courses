package org.opensources.courses.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-256/GCM encryption with a non-exportable key held by the Android Keystore.
 *
 * Only the ciphertext (IV + encrypted bytes, Base64) reaches the dedicated `secrets` DataStore file,
 * which is itself excluded from backups: a restored payload could never be decrypted anyway. If
 * decryption fails (key lost after a restore, corrupted data) the entry is removed and treated as
 * absent, so the user is simply asked for the token again.
 */
@Singleton
class KeystoreSecretStore
    @Inject
    constructor(
        @SecretsDataStore private val dataStore: DataStore<Preferences>,
    ) : SecretStore {
        private val keyStore: KeyStore by lazy { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) } }

        override suspend fun read(name: String): String? {
            val encoded = dataStore.data.first()[stringPreferencesKey(name)] ?: return null
            return runCatching { decrypt(encoded) }.getOrElse {
                remove(name)
                null
            }
        }

        override suspend fun write(
            name: String,
            value: String,
        ) {
            val encrypted = encrypt(value)
            dataStore.edit { it[stringPreferencesKey(name)] = encrypted }
        }

        override suspend fun remove(name: String) {
            dataStore.edit { it.remove(stringPreferencesKey(name)) }
        }

        private fun encrypt(value: String): String {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val encoder = Base64.getEncoder()
            return encoder.encodeToString(cipher.iv) + SEPARATOR + encoder.encodeToString(encrypted)
        }

        private fun decrypt(encoded: String): String {
            val (iv, payload) = encoded.split(SEPARATOR).map { Base64.getDecoder().decode(it) }
            val key = keyStore.getKey(KEY_ALIAS, null) as? SecretKey ?: error("Missing key")
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            return String(cipher.doFinal(payload), Charsets.UTF_8)
        }

        private fun getOrCreateKey(): SecretKey =
            keyStore.getKey(KEY_ALIAS, null) as? SecretKey
                ?: KeyGenerator
                    .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                    .apply {
                        init(
                            KeyGenParameterSpec
                                .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                                .setKeySize(KEY_SIZE_BITS)
                                .build(),
                        )
                    }.generateKey()

        private companion object {
            const val ANDROID_KEYSTORE = "AndroidKeyStore"
            const val KEY_ALIAS = "courses.secrets"
            const val TRANSFORMATION = "AES/GCM/NoPadding"
            const val GCM_TAG_BITS = 128
            const val KEY_SIZE_BITS = 256
            const val SEPARATOR = ":"
        }
    }
