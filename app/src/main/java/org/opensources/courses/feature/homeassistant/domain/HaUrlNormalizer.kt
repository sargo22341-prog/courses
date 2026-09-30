package org.opensources.courses.feature.homeassistant.domain

import java.net.URI
import java.net.URISyntaxException

object HaUrlNormalizer {
    /**
     * `homeassistant.local:8123/` → `http://homeassistant.local:8123`. Returns null when the text
     * cannot be an http(s) address, or carries credentials (`user:password@`), a query or a fragment:
     * they would be saved in clear, and request paths are appended to the address.
     */
    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        // The scheme is detected before trailing slashes are removed: "https://" alone must not
        // lose its "//" and be read as the host "https".
        val withScheme = (if (SCHEME.containsMatchIn(trimmed)) trimmed else "http://$trimmed").trimEnd('/')
        val uri = parse(withScheme) ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
        if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
        return withScheme
    }

    /**
     * True when both addresses name the same server, whatever their scheme or port: a token saved for
     * one address is never sent to another server.
     */
    fun sameHost(
        first: String,
        second: String,
    ): Boolean {
        val firstHost = host(first) ?: return false
        return firstHost.equals(host(second), ignoreCase = true)
    }

    private fun host(address: String): String? = normalize(address)?.let(::parse)?.host

    /** Text typed by the user: a malformed address is an expected answer, not an error. */
    private fun parse(address: String): URI? =
        try {
            URI(address)
        } catch (_: URISyntaxException) {
            null
        }

    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
}
