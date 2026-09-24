package org.opensources.courses.feature.homeassistant.data.sync

import org.opensources.courses.feature.homeassistant.domain.HaItemContent
import org.opensources.courses.feature.homeassistant.domain.HaItemFormat
import org.opensources.courses.feature.homeassistant.domain.HaTodoItem
import org.opensources.courses.feature.homeassistant.domain.LocalItemState
import org.opensources.courses.feature.homeassistant.domain.RemoteItemState

/** When the remote item does not tell the quantity (no description, no leading quantity), the local one is kept. */
fun HaTodoItem.toRemoteState(
    local: SyncItemRef,
    format: HaItemFormat,
): RemoteItemState {
    val content = format.read(this, local.content())
    val quantity = content.quantity
    return if (quantity == null) {
        RemoteItemState(content.name, completed, local.quantity, local.unit, completedAt)
    } else {
        RemoteItemState(content.name, completed, quantity, content.unit, completedAt)
    }
}

fun SyncItemRef.content(): HaItemContent = HaItemContent(name, quantity, unit)

fun SyncItemRef.toLocalState(hasPendingChanges: Boolean): LocalItemState =
    LocalItemState(
        name = name,
        checked = isChecked,
        quantity = quantity,
        unit = unit,
        hasPendingChanges = hasPendingChanges,
        isDeleted = isDeleted,
    )
