package org.opensources.courses.feature.homeassistant.data.sync

data class SyncListRef(
    val localId: String,
    val name: String,
    val remoteId: String?,
)

data class SyncItemRef(
    val localId: String,
    val listLocalId: String,
    val name: String,
    val quantity: Double,
    val unit: String?,
    val isChecked: Boolean,
    val remoteId: String?,
    val isDeleted: Boolean,
)

/**
 * Local side of the synchronisation. Methods that apply remote data re-check, inside their own
 * transaction, that no local operation was queued meanwhile: a change made by the user while a
 * synchronisation is running is never overwritten.
 */
interface SyncLocalStore {
    suspend fun synchronizedLists(): List<SyncListRef>

    /** Items of the list, tombstones included. */
    suspend fun items(listLocalId: String): List<SyncItemRef>

    suspend fun setListRemote(
        listLocalId: String,
        entityId: String,
        configEntryId: String?,
        name: String,
    )

    suspend fun markListSynced(listLocalId: String)

    /** The remote list disappeared: keep everything locally, stop synchronising. */
    suspend fun unlinkList(listLocalId: String)

    suspend fun forgetTrackedList(entityId: String)

    suspend fun setItemRemoteId(
        itemLocalId: String,
        remoteId: String?,
    )

    /** Removes a tombstone whose deletion reached the remote. */
    suspend fun purgeItem(itemLocalId: String)

    suspend fun markItemSynced(itemLocalId: String)

    suspend fun removeRemotelyDeletedItem(itemLocalId: String)

    suspend fun applyRemoteItem(
        itemLocalId: String,
        name: String,
        quantity: Double,
        unit: String?,
        checked: Boolean,
    )

    suspend fun insertRemoteItem(
        listLocalId: String,
        remoteId: String,
        name: String,
        quantity: Double,
        unit: String?,
        checked: Boolean,
    )

    /** Deleted remotely while modified locally: queue its creation again. */
    suspend fun requeueCreation(itemLocalId: String)
}
