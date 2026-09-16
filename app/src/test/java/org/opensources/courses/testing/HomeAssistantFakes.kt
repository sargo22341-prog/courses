package org.opensources.courses.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import org.opensources.courses.core.sync.SyncOperationType
import org.opensources.courses.core.sync.SyncQueue
import org.opensources.courses.feature.homeassistant.data.sync.SyncItemRef
import org.opensources.courses.feature.homeassistant.data.sync.SyncListRef
import org.opensources.courses.feature.homeassistant.data.sync.SyncLocalStore
import org.opensources.courses.feature.homeassistant.domain.HaConfigRepository
import org.opensources.courses.feature.homeassistant.domain.HaCredentials
import org.opensources.courses.feature.homeassistant.domain.HaListMode
import org.opensources.courses.feature.homeassistant.domain.HaListNameAllocator
import org.opensources.courses.feature.homeassistant.domain.HaLiveUpdates
import org.opensources.courses.feature.homeassistant.domain.HaUrlNormalizer
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantConfig

class FakeHaConfigRepository(
    var storedCredentials: HaCredentials? = HaCredentials("http://ha.local:8123", "token"),
) : HaConfigRepository {
    override val config = MutableStateFlow(HomeAssistantConfig(true, "http://ha.local:8123", true, HaListMode.APP_CREATED_ONLY, autoSync = true))

    override suspend fun credentials(): HaCredentials? = storedCredentials

    override suspend fun credentialsFor(
        baseUrl: String,
        typedToken: String,
    ): HaCredentials? = storedCredentials

    var forgotten = false

    override suspend fun saveConnection(
        baseUrl: String,
        token: String,
    ): Boolean {
        val url = HaUrlNormalizer.normalize(baseUrl) ?: return false
        if (token.isNotBlank()) storedCredentials = HaCredentials(url, token.trim())
        config.value = config.value.copy(baseUrl = url, hasToken = config.value.hasToken || token.isNotBlank())
        return true
    }

    override suspend fun forgetConnection() {
        forgotten = true
        storedCredentials = null
        config.value = HomeAssistantConfig.Default
    }

    override suspend fun setEnabled(enabled: Boolean) {
        config.value = config.value.copy(enabled = enabled)
    }

    override suspend fun setListMode(mode: HaListMode) {
        config.value = config.value.copy(listMode = mode)
    }

    override suspend fun setAutoSync(enabled: Boolean) = Unit

    override suspend fun setAutoCreateLists(enabled: Boolean) {
        config.value = config.value.copy(autoCreateLists = enabled)
    }

    override suspend fun setListsSetupDone() {
        config.value = config.value.copy(listsSetupDone = true)
    }
}

/** Live updates driven by the test: [changes] emissions reach the collectors. */
class FakeHaLiveUpdates : HaLiveUpdates {
    val changes = MutableSharedFlow<Unit>()
    var observedEntityIds: Set<String>? = null

    override fun observeItemChanges(
        credentials: HaCredentials,
        entityIds: Set<String>,
    ): Flow<Unit> = changes.onStart { observedEntityIds = entityIds }
}

/** Same contract as the Room store, including "never overwrite an item with pending operations". */
class FakeSyncLocalStore(
    private val queue: SyncQueue,
) : SyncLocalStore {
    val lists = linkedMapOf<String, SyncListRef>()
    val items = linkedMapOf<String, SyncItemRef>()
    val catalogProductIds = mutableMapOf<String, String?>()
    val tracked = mutableSetOf<String>()
    val unlinked = mutableSetOf<String>()
    val ignored = mutableSetOf<String>()
    val removedLists = mutableSetOf<String>()
    private var nextId = 1

    override suspend fun synchronizedLists(): List<SyncListRef> = lists.values.toList()

    override suspend fun ignoredEntityIds(): Set<String> = ignored.toSet()

    override suspend fun ignoreList(entityId: String) {
        ignored += entityId
    }

    override suspend fun importList(
        entityId: String,
        remoteName: String,
    ) {
        if (entityId in ignored || lists.values.any { it.remoteId == entityId }) return
        val id = "imported${nextId++}"
        val name = HaListNameAllocator.uniqueName(remoteName, lists.values.map { it.name })
        lists[id] = SyncListRef(id, name, entityId, importedFromRemote = true, remoteName = remoteName)
    }

    override suspend fun applyRemoteListName(
        listLocalId: String,
        remoteName: String,
    ) {
        val list = lists.getValue(listLocalId)
        if (!list.importedFromRemote || list.remoteName == remoteName) return
        val name = HaListNameAllocator.uniqueName(remoteName, lists.values.filter { it.localId != listLocalId }.map { it.name })
        lists[listLocalId] = list.copy(name = name, remoteName = remoteName)
    }

    override suspend fun removeRemotelyDeletedList(listLocalId: String) {
        if (queue.pending().any { it.listLocalId == listLocalId && it.type.isItemOperation }) {
            unlinkList(listLocalId)
        } else {
            removeList(listLocalId)
        }
    }

    override suspend fun removeImportedLists() {
        lists.values.filter { it.importedFromRemote }.forEach { removeList(it.localId) }
    }

    private suspend fun removeList(listLocalId: String) {
        lists.remove(listLocalId)
        items.values.removeAll { it.listLocalId == listLocalId }
        removedLists += listLocalId
        queue.clearList(listLocalId)
    }

    override fun observeLinkedEntityIds(): Flow<Set<String>> = flowOf(lists.values.mapNotNull { it.remoteId }.toSet())

    override suspend fun items(listLocalId: String): List<SyncItemRef> = items.values.filter { it.listLocalId == listLocalId }

    override suspend fun setListRemote(
        listLocalId: String,
        entityId: String,
        configEntryId: String?,
        name: String,
    ) {
        lists[listLocalId] = lists.getValue(listLocalId).copy(remoteId = entityId)
        tracked += entityId
    }

    override suspend fun markListSynced(listLocalId: String) = Unit

    override suspend fun unlinkList(listLocalId: String) {
        lists.remove(listLocalId)
        unlinked += listLocalId
        queue.clearList(listLocalId)
    }

    override suspend fun forgetTrackedList(entityId: String) {
        tracked -= entityId
    }

    override suspend fun setItemRemoteId(
        itemLocalId: String,
        remoteId: String?,
    ) {
        items[itemLocalId]?.let { items[itemLocalId] = it.copy(remoteId = remoteId) }
    }

    override suspend fun purgeItem(itemLocalId: String) {
        items.remove(itemLocalId)
    }

    override suspend fun restoreDeletedItem(itemLocalId: String) {
        items[itemLocalId]?.let { items[itemLocalId] = it.copy(isDeleted = false) }
    }

    override suspend fun abandonItemChanges(
        itemLocalId: String,
        operationIds: List<Long>,
    ) {
        queue.complete(operationIds)
        restoreDeletedItem(itemLocalId)
    }

    override suspend fun markItemSynced(itemLocalId: String) = Unit

    override suspend fun removeRemotelyDeletedItem(itemLocalId: String) {
        if (!queue.hasPendingForItem(itemLocalId)) items.remove(itemLocalId)
    }

    override suspend fun applyRemoteItem(
        itemLocalId: String,
        name: String,
        quantity: Double,
        unit: String?,
        checked: Boolean,
    ) {
        if (queue.hasPendingForItem(itemLocalId)) return
        items[itemLocalId] = items.getValue(itemLocalId).copy(name = name, quantity = quantity, unit = unit, isChecked = checked)
    }

    override suspend fun insertRemoteItem(
        listLocalId: String,
        remoteId: String,
        name: String,
        quantity: Double,
        unit: String?,
        checked: Boolean,
        catalogProductId: String?,
    ) {
        val id = "pulled${nextId++}"
        items[id] = SyncItemRef(id, listLocalId, name, quantity, unit, checked, remoteId, isDeleted = false)
        catalogProductIds[id] = catalogProductId
    }

    override suspend fun requeueCreation(itemLocalId: String) {
        val item = items.getValue(itemLocalId).copy(remoteId = null)
        items[itemLocalId] = item
        queue.enqueue(SyncOperationType.CREATE_ITEM, item.listLocalId, itemLocalId)
    }
}
