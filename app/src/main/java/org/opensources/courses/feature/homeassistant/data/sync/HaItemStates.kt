package org.opensources.courses.feature.homeassistant.data.sync

import org.opensources.courses.feature.homeassistant.domain.HaTodoItem
import org.opensources.courses.feature.homeassistant.domain.ItemDescriptionCodec
import org.opensources.courses.feature.homeassistant.domain.ItemQuantity
import org.opensources.courses.feature.homeassistant.domain.LocalItemState
import org.opensources.courses.feature.homeassistant.domain.RemoteItemState

/** Without description support the remote has no quantity: the local one is kept. */
fun HaTodoItem.toRemoteState(
    local: SyncItemRef,
    supportsDescription: Boolean,
): RemoteItemState {
    val quantity = if (supportsDescription) ItemDescriptionCodec.decode(description) else ItemQuantity(local.quantity, local.unit)
    return RemoteItemState(summary, completed, quantity.quantity, quantity.unit, completedAt)
}

fun SyncItemRef.toLocalState(hasPendingChanges: Boolean): LocalItemState =
    LocalItemState(
        name = name,
        checked = isChecked,
        quantity = quantity,
        unit = unit,
        hasPendingChanges = hasPendingChanges,
        isDeleted = isDeleted,
    )
