package org.opensources.courses.feature.homeassistant.domain

data class LocalItemState(
    val name: String,
    val checked: Boolean,
    val quantity: Double,
    val unit: String?,
    val hasPendingChanges: Boolean,
    val isDeleted: Boolean,
)

data class RemoteItemState(
    val summary: String,
    val completed: Boolean,
    val quantity: Double,
    val unit: String?,
)

enum class ItemResolution {
    /** Local and remote are equal: the item is synchronised. */
    IN_SYNC,

    /** A local change is still waiting to be pushed: remote data is ignored for now. */
    KEEP_LOCAL,

    /** Remote changed and the local copy has no pending change: take the remote values. */
    APPLY_REMOTE,

    /** Deleted remotely (or deletion confirmed): remove the local copy. */
    DELETE_LOCAL,

    /** Deleted remotely while modified locally: the local modification wins, recreate it. */
    RECREATE_REMOTE,
}

/**
 * Conflict strategy: **last write wins, except that a local change not yet synchronised is never
 * overwritten.**
 *
 * Home Assistant exposes no modification date on to-do items, so "last write" is decided by the
 * synchronisation order: pending local operations are pushed first (local change = latest), then
 * remote state is read and applied to every item that has no pending operation left (remote
 * state = latest). The outcome depends only on the two states, never on timing, which keeps it
 * deterministic and testable.
 */
object ConflictResolver {
    fun resolve(
        local: LocalItemState,
        remote: RemoteItemState?,
        compareQuantity: Boolean,
    ): ItemResolution =
        when {
            local.isDeleted -> if (remote == null) ItemResolution.DELETE_LOCAL else ItemResolution.KEEP_LOCAL
            local.hasPendingChanges -> if (remote == null) ItemResolution.RECREATE_REMOTE else ItemResolution.KEEP_LOCAL
            remote == null -> ItemResolution.DELETE_LOCAL
            isEqual(local, remote, compareQuantity) -> ItemResolution.IN_SYNC
            else -> ItemResolution.APPLY_REMOTE
        }

    private fun isEqual(
        local: LocalItemState,
        remote: RemoteItemState,
        compareQuantity: Boolean,
    ): Boolean =
        local.name == remote.summary &&
            local.checked == remote.completed &&
            (!compareQuantity || (local.quantity == remote.quantity && local.unit.orEmpty() == remote.unit.orEmpty()))
}
