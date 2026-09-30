package org.opensources.courses.feature.homeassistant.data.sync

import kotlinx.coroutines.flow.Flow
import org.opensources.courses.core.model.SyncStatus

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

/**
 * @property syncStatus as stored; [SyncStatus.PENDING] when not known, which never skips a write.
 * @property catalogProductId the catalog product the item stands for, if any.
 */
data class SyncItemRef(
    val localId: String,
    val listLocalId: String,
    val name: String,
    val quantity: Double,
    val unit: String?,
    val isChecked: Boolean,
    val remoteId: String?,
    val isDeleted: Boolean,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val catalogProductId: String? = null,
)

/**
 * Local side of the synchronisation. Methods that apply remote data re-check, inside their own
 * transaction, that no local operation was queued meanwhile: a change made by the user while a
 * synchronisation is running is never overwritten.
 */
interface SyncLocalStore {
    /** Runs [block], and the calls it makes to this store, in a single transaction. */
    suspend fun <T> inTransaction(block: suspend () -> T): T

    suspend fun synchronizedLists(): List<SyncListRef>

    /** Entity ids of the Home Assistant lists linked to a local list. */
    fun observeLinkedEntityIds(): Flow<Set<String>>

    /** Items of the list, tombstones included. */
    suspend fun items(listLocalId: String): List<SyncItemRef>

    /**
     * Links the list to the Home Assistant list just created for it and returns true. A list deleted
     * in the app while it was being created returns false: the deletion of the new Home Assistant list
     * is queued instead, so it is neither left behind nor imported by the "all lists" mode.
     */
    suspend fun linkCreatedList(
        listLocalId: String,
        entityId: String,
        configEntryId: String?,
    ): Boolean

    suspend fun markListSynced(listLocalId: String)

    /** Home Assistant lists the user removed from the app or unlinked: never imported again. */
    suspend fun ignoredEntityIds(): Set<String>

    suspend fun ignoreList(entityId: String)

    /**
     * Integrations already known of [entityIds] ([saveIntegrations]); an entity not asked yet is
     * absent, one Home Assistant could not tell maps to null.
     */
    suspend fun knownIntegrations(entityIds: Collection<String>): Map<String, String?>

    suspend fun saveIntegrations(integrations: Map<String, String?>)

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

    /**
     * Links [item] to the Home Assistant item [uid] just created for it and returns true. An item
     * deleted in the app while it was being created returns false: it comes back as a tombstone holding
     * what Home Assistant now holds, with its deletion queued, so the new Home Assistant item is deleted
     * rather than imported again as a new item.
     */
    suspend fun linkCreatedItem(
        item: SyncItemRef,
        uid: String,
    ): Boolean

    /** Removes a tombstone whose deletion reached the remote. */
    suspend fun purgeItem(itemLocalId: String)

    /** Cancels a local deletion: the item was modified in Home Assistant meanwhile. */
    suspend fun restoreDeletedItem(itemLocalId: String)

    /**
     * Home Assistant kept refusing the changes of the item: [operationIds] leave the queue and, in the
     * same transaction, a refused deletion is cancelled. A linked item then takes the Home Assistant
     * state at the reconciliation; an item never created there stays on this phone only.
     */
    suspend fun abandonItemChanges(
        itemLocalId: String,
        operationIds: List<Long>,
    )

    suspend fun markItemSynced(itemLocalId: String)

    suspend fun removeRemotelyDeletedItem(itemLocalId: String)

    /**
     * Takes the remote values. A new name drops the catalog link as any rename does, unless
     * [catalogProductId] tells the product the new name stands for.
     */
    suspend fun applyRemoteItem(
        itemLocalId: String,
        name: String,
        quantity: Double,
        unit: String?,
        checked: Boolean,
        catalogProductId: String? = null,
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

    /**
     * Links the item to the catalog product [catalogProductId], if it is still named [expectedName].
     * Local only, like any catalog link: nothing is sent. Returns whether the link was written.
     */
    suspend fun linkItemToProduct(
        itemLocalId: String,
        expectedName: String,
        catalogProductId: String,
    ): Boolean

    /** Deleted remotely while modified locally: queue its creation again. */
    suspend fun requeueCreation(itemLocalId: String)
}
