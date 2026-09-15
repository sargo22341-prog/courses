package org.opensources.courses.feature.homeassistant.domain

/**
 * @property supportsDescription whether items of this list accept a description (Local To-do does,
 * the legacy Shopping list integration does not). Quantities are stored there when possible.
 */
data class HaTodoList(
    val entityId: String,
    val name: String,
    val supportsDescription: Boolean,
    /** False when its integration is stopped (`unavailable`): its items cannot be read or written. */
    val isAvailable: Boolean = true,
    /** False when items cannot be added, changed and removed (read-only list): it is never imported. */
    val isEditable: Boolean = true,
)

/** @property completedAt when the item was completed (epoch millis): the only date Home Assistant keeps on items. */
data class HaTodoItem(
    val uid: String,
    val summary: String,
    val completed: Boolean,
    val description: String?,
    val completedAt: Long? = null,
)

/** @property name the name actually used in Home Assistant (see [HaListNameAllocator]). */
data class HaCreatedList(
    val entityId: String,
    val configEntryId: String?,
    val name: String,
)
