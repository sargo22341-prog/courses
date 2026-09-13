package org.opensources.courses.feature.homeassistant.domain

/**
 * Reads a long-lived access token from a scanned QR code.
 *
 * Home Assistant's "long-lived access token" dialog encodes the raw token in its QR code. Tokens
 * are JWTs (three base64url segments separated by dots); anything else — a Wi-Fi QR code, a URL —
 * is rejected so a wrong scan never silently replaces the token being typed.
 */
object HaTokenParser {
    private val JWT = Regex("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$")
    private const val BEARER_PREFIX = "Bearer "
    private const val MIN_LENGTH = 30

    fun parse(scanned: String): String? =
        scanned
            .trim()
            .removePrefix(BEARER_PREFIX)
            .trim()
            .takeIf { it.length >= MIN_LENGTH && JWT.matches(it) }
}
