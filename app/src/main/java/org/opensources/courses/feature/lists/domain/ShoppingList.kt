package org.opensources.courses.feature.lists.domain

import org.opensources.courses.core.model.SyncStatus

data class ShoppingList(
    val id: String,
    val name: String,
    val isDefault: Boolean,
    val remoteId: String?,
    val createdByApp: Boolean,
    val syncStatus: SyncStatus,
) {
    /** Linked to Home Assistant, or waiting for its creation there. */
    val isSynchronized: Boolean get() = syncStatus != SyncStatus.LOCAL_ONLY
}
