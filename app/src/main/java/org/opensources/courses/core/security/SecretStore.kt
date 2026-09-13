package org.opensources.courses.core.security

/** Stores small secrets (the Home Assistant token) encrypted at rest. */
interface SecretStore {
    suspend fun read(name: String): String?

    suspend fun write(
        name: String,
        value: String,
    )

    suspend fun remove(name: String)
}
