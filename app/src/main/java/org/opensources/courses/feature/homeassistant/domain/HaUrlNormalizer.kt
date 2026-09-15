package org.opensources.courses.feature.homeassistant.domain

import java.net.URI

object HaUrlNormalizer {
    /**
     * `homeassistant.local:8123/` → `http://homeassistant.local:8123`. Returns null when the text
     * cannot be an http(s) address.
     */
    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        // The scheme is detected before trailing slashes are removed: "https://" alone must not
        // lose its "//" and be read as the host "https".
        val withScheme = (if (SCHEME.containsMatchIn(trimmed)) trimmed else "http://$trimmed").trimEnd('/')
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
        return withScheme
    }

    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
}
