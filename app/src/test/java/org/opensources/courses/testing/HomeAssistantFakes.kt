package org.opensources.courses.testing

import kotlinx.coroutines.flow.MutableStateFlow
import org.opensources.courses.core.sync.SyncOperationType
import org.opensources.courses.core.sync.SyncQueue
import org.opensources.courses.feature.homeassistant.data.sync.SyncItemRef
import org.opensources.courses.feature.homeassistant.data.sync.SyncListRef
import org.opensources.courses.feature.homeassistant.data.sync.SyncLocalStore
import org.opensources.courses.feature.homeassistant.domain.HaConfigRepository
import org.opensources.courses.feature.homeassistant.domain.HaCreatedList
import org.opensources.courses.feature.homeassistant.domain.HaCredentials
import org.opensources.courses.feature.homeassistant.domain.HaErrorKind
import org.opensources.courses.feature.homeassistant.domain.HaListMode
import org.opensources.courses.feature.homeassistant.domain.HaListNameAllocator
import org.opensources.courses.feature.homeassistant.domain.HaTodoItem
import org.opensources.courses.feature.homeassistant.domain.HaTodoList
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantConfig
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantException
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantGateway

class FakeHaConfigRepository(
    var storedCredentials: HaCredentials? = HaCredentials("http://ha.local:8123", "token"),
) : HaConfigRepository {
    override val config = MutableStateFlow(HomeAssistantConfig(true, "http://ha.local:8123", true, HaListMode.ALL_LISTS, autoSync = true))

    override suspend fun credentials(): HaCredentials? = storedCredentials

    override suspend fun credentialsFor(
        baseUrl: String,
        typedToken: String,
    ): HaCredentials? = storedCredentials

    override suspend fun saveConnection(
        baseUrl: String,
        token: String,
    ) = Unit

    override suspend fun setEnabled(enabled: Boolean) = Unit

    override suspend fun setListMode(mode: HaListMode) = Unit

    override suspend fun setAutoSync(enabled: Boolean) = Unit

    override suspend fun setAutoCreateLists(enabled: Boolean) {
        config.value = config.value.copy(autoCreateLists = enabled)
    }

    override suspend fun setListsSetupDone() {
        config.value = config.value.copy(listsSetupDone = true)
    }
}

/** A tiny in-memory Home Assistant with the to-do semantics the engine relies on. */
class FakeHomeAssistantGateway : HomeAssistantGateway {
    val lists = mutableMapOf<String, HaTodoList>()
    val items = mutableMapOf<String, MutableList<HaTodoItem>>()
    val deletedEntries = mutableListOf<String>()
    var failure: HaErrorKind? = null
    var failingUpdates: HaErrorKind? = null
    private var nextUid = 1

    fun addRemote(
        entityId: String,
        summary: String,
        completed: Boolean = false,
        description: String? = null,
    ): String {
        val uid = "uid${nextUid++}"
        items.getOrPut(entityId) { mutableListOf() } += HaTodoItem(uid, summary, completed, description)
        return uid
    }

    fun remote(entityId: String): List<HaTodoItem> = items[entityId].orEmpty()

    private fun check() {
        failure?.let { throw HomeAssistantException(it) }
    }

    override suspend fun testConnection(credentials: HaCredentials) = check()

    override suspend fun getTodoLists(credentials: HaCredentials): List<HaTodoList> {
        check()
        return lists.values.toList()
    }

    override suspend fun getItems(
        credentials: HaCredentials,
        entityId: String,
    ): List<HaTodoItem> {
        check()
        return items[entityId].orEmpty().toList()
    }

    override suspend fun addItem(
        credentials: HaCredentials,
        entityId: String,
        summary: String,
        description: String?,
    ) {
        check()
        addRemote(entityId, summary, description = description)
    }

    override suspend fun updateItem(
        credentials: HaCredentials,
        entityId: String,
        uid: String,
        summary: String,
        completed: Boolean,
        description: String?,
        sendDescription: Boolean,
    ) {
        check()
        failingUpdates?.let { throw HomeAssistantException(it) }
        val list = items.getValue(entityId)
        val index = list.indexOfFirst { it.uid == uid }
        if (index < 0) throw HomeAssistantException(HaErrorKind.REJECTED)
        list[index] = list[index].copy(summary = summary, completed = completed, description = if (sendDescription) description else list[index].description)
    }

    override suspend fun removeItem(
        credentials: HaCredentials,
        entityId: String,
        uid: String,
    ) {
        check()
        items[entityId]?.removeAll { it.uid == uid }
    }

    override suspend fun createList(
        credentials: HaCredentials,
        name: String,
    ): HaCreatedList {
        check()
        val unique = HaListNameAllocator.uniqueName(name, lists.values.map { it.name })
        val entityId = "todo.${unique.lowercase().replace(' ', '_')}"
        lists[entityId] = HaTodoList(entityId, unique, supportsDescription = true)
        return HaCreatedList(entityId, "entry-$entityId", unique)
    }

    override suspend fun deleteList(
        credentials: HaCredentials,
        configEntryId: String,
    ) {
        check()
        deletedEntries += configEntryId
    }
}

/** Same contract as the Room store, including "never overwrite an item with pending operations". */
class FakeSyncLocalStore(
    private val queue: SyncQueue,
) : SyncLocalStore {
    val lists = linkedMapOf<String, SyncListRef>()
    val items = linkedMapOf<String, SyncItemRef>()
    val tracked = mutableSetOf<String>()
    val unlinked = mutableSetOf<String>()
    private var nextId = 1

    override suspend fun synchronizedLists(): List<SyncListRef> = lists.values.toList()

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
    ) {
        val id = "pulled${nextId++}"
        items[id] = SyncItemRef(id, listLocalId, name, quantity, unit, checked, remoteId, isDeleted = false)
    }

    override suspend fun requeueCreation(itemLocalId: String) {
        val item = items.getValue(itemLocalId).copy(remoteId = null)
        items[itemLocalId] = item
        queue.enqueue(SyncOperationType.CREATE_ITEM, item.listLocalId, itemLocalId)
    }
}
