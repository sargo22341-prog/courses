package org.opensources.courses.feature.homeassistant.data.sync

import kotlinx.coroutines.flow.Flow

/**
 * @property importedFromRemote added by the "all lists" mode: its name follows Home Assistant.
 * @property remoteName Home Assistant name last applied to an imported list.
 */
data class SyncListRef(
    val localId: String,
    val name: String,
    val remoteId: String?,
    val importedFromRemote: Boolean = false,
    val remoteName: String? = null,
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

    /** Entity ids of the Home Assistant lists linked to a local list. */
    fun observeLinkedEntityIds(): Flow<Set<String>>

    /** Items of the list, tombstones included. */
    suspend fun items(listLocalId: String): List<SyncItemRef>

    suspend fun setListRemote(
        listLocalId: String,
        entityId: String,
        configEntryId: String?,
        name: String,
    )

    suspend fun markListSynced(listLocalId: String)

    /** Home Assistant lists the user removed from the app or unlinked: never imported again. */
    suspend fun ignoredEntityIds(): Set<String>

    suspend fun ignoreList(entityId: String)

    /**
     * "All lists" mode: adds the Home Assistant list to the app, linked, under its Home Assistant name
     * or a free variant of it (« Courses 2 »). Does nothing if it is already linked or ignored.
     */
    suspend fun importList(
        entityId: String,
        remoteName: String,
    )

    /** Home Assistant renamed an imported list: the local name follows, kept unique. */
    suspend fun applyRemoteListName(
        listLocalId: String,
        remoteName: String,
    )

    /** The remote list disappeared and the list must stay: keep everything locally, stop synchronising. */
    suspend fun unlinkList(listLocalId: String)

    /**
     * The remote list disappeared in "all lists" mode: the local list goes too, unless it holds
     * changes not sent yet or is the last list, which are only unlinked.
     */
    suspend fun removeRemotelyDeletedList(listLocalId: String)

    /** The "all lists" mode was left: removes the lists it imported. */
    suspend fun removeImportedLists()

    suspend fun forgetTrackedList(entityId: String)

    suspend fun setItemRemoteId(
        itemLocalId: String,
        remoteId: String?,
    )

    /** Removes a tombstone whose deletion reached the remote. */
    suspend fun purgeItem(itemLocalId: String)

    /** Cancels a local deletion: the item was modified in Home Assistant meanwhile. */
    suspend fun restoreDeletedItem(itemLocalId: String)

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
        catalogProductId: String?,
    )

    /** Deleted remotely while modified locally: queue its creation again. */
    suspend fun requeueCreation(itemLocalId: String)
}
