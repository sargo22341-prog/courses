package org.opensources.courses.feature.homeassistant.domain

/**
 * @property supportsDescription whether items of this list accept a description (Local To-do does,
 * the legacy Shopping list integration does not). Quantities are stored there when possible.
 */
data class HaTodoList(
    val entityId: String,
    val name: String,
    val supportsDescription: Boolean,
)

data class HaTodoItem(
    val uid: String,
    val summary: String,
    val completed: Boolean,
    val description: String?,
)

/** @property name the name actually used in Home Assistant (see [HaListNameAllocator]). */
data class HaCreatedList(
    val entityId: String,
    val configEntryId: String?,
    val name: String,
)
