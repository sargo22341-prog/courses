package org.opensources.courses.feature.lists.domain

object ListNameRules {
    const val MAX_LENGTH = 60

    /** Trimmed name, or null when it cannot be used as a list name. */
    fun sanitize(raw: String): String? = raw.trim().replace(WHITESPACE, " ").takeIf { it.isNotEmpty() }?.take(MAX_LENGTH)

    private val WHITESPACE = Regex("\\s+")
}
