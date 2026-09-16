package org.opensources.courses.core.sync

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One pending local change waiting to be sent to the remote (Home Assistant).
 *
 * Operations are never lost: they are deleted only once the remote confirmed them. Remote
 * identifiers are copied here at enqueue time so a deletion can still be sent after the local row
 * disappeared (deleted list, purged item).
 */
@Entity(
    tableName = "sync_operations",
    indices = [Index("listLocalId"), Index("itemLocalId")],
)
data class SyncOperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: SyncOperationType,
    val listLocalId: String,
    val itemLocalId: String?,
    val remoteListId: String?,
    val remoteItemId: String?,
    val remoteEntryId: String?,
    val createdAt: Long,
    val attemptCount: Int = 0,
    val lastError: String? = null,
)

data class SyncOperation(
    val id: Long,
    val type: SyncOperationType,
    val listLocalId: String,
    val itemLocalId: String?,
    val remoteListId: String?,
    val remoteItemId: String?,
    val remoteEntryId: String?,
    val attemptCount: Int,
    /** When the local change was made (epoch millis). */
    val createdAt: Long,
) {
    /** One more refusal and the remote has refused it [SyncQueue.MAX_ATTEMPTS] times: it is given up instead. */
    val isLastAttempt: Boolean get() = attemptCount + 1 >= SyncQueue.MAX_ATTEMPTS
}

fun SyncOperationEntity.toDomain(): SyncOperation =
    SyncOperation(
        id = id,
        type = type,
        listLocalId = listLocalId,
        itemLocalId = itemLocalId,
        remoteListId = remoteListId,
        remoteItemId = remoteItemId,
        remoteEntryId = remoteEntryId,
        attemptCount = attemptCount,
        createdAt = createdAt,
    )
