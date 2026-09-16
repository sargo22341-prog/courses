package org.opensources.courses.testing

import org.opensources.courses.feature.homeassistant.domain.HaCreatedList
import org.opensources.courses.feature.homeassistant.domain.HaCredentials
import org.opensources.courses.feature.homeassistant.domain.HaErrorKind
import org.opensources.courses.feature.homeassistant.domain.HaListNameAllocator
import org.opensources.courses.feature.homeassistant.domain.HaTodoItem
import org.opensources.courses.feature.homeassistant.domain.HaTodoList
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantException
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantGateway

/** A tiny in-memory Home Assistant with the to-do semantics the engine relies on. */
class FakeHomeAssistantGateway : HomeAssistantGateway {
    val lists = mutableMapOf<String, HaTodoList>()
    val items = mutableMapOf<String, MutableList<HaTodoItem>>()
    val deletedEntries = mutableListOf<String>()
    var failure: HaErrorKind? = null
    var failingUpdates: HaErrorKind? = null

    /** Items whose next update is refused once, with [HaErrorKind.REJECTED]. */
    val refusedOnceUids = mutableSetOf<String>()
    var failingDeletions: HaErrorKind? = null
    var failingAdditions: HaErrorKind? = null
    var failingRemovals: HaErrorKind? = null
    var failingListCreations: HaErrorKind? = null

    /** What Home Assistant stores for a text sent by `add_item` (some integrations rewrite it). */
    var storedSummary: (String) -> String = { it }

    /** Number of `get_items` calls. */
    var itemReads = 0
        private set

    /** Number of times the lists were read (`/api/states`). */
    var listReads = 0
        private set
    private var nextUid = 1

    fun addRemote(
        entityId: String,
        summary: String,
        completed: Boolean = false,
        description: String? = null,
        completedAt: Long? = null,
    ): String {
        val uid = "uid${nextUid++}"
        items.getOrPut(entityId) { mutableListOf() } += HaTodoItem(uid, summary, completed, description, completedAt)
        return uid
    }

    fun remote(entityId: String): List<HaTodoItem> = items[entityId].orEmpty()

    private fun check() {
        failure?.let { throw HomeAssistantException(it) }
    }

    override suspend fun testConnection(credentials: HaCredentials) = check()

    override suspend fun getTodoLists(credentials: HaCredentials): List<HaTodoList> {
        check()
        listReads++
        return lists.values.toList()
    }

    override suspend fun getItems(
        credentials: HaCredentials,
        entityId: String,
    ): List<HaTodoItem> {
        check()
        itemReads++
        return items[entityId].orEmpty().toList()
    }

    override suspend fun addItem(
        credentials: HaCredentials,
        entityId: String,
        summary: String,
        description: String?,
    ) {
        check()
        failingAdditions?.let { throw HomeAssistantException(it) }
        addRemote(entityId, storedSummary(summary), description = description)
    }

    override suspend fun updateItem(
        credentials: HaCredentials,
        entityId: String,
        uid: String,
        summary: String?,
        completed: Boolean?,
        description: String?,
        sendDescription: Boolean,
    ) {
        check()
        failingUpdates?.let { throw HomeAssistantException(it) }
        if (refusedOnceUids.remove(uid)) throw HomeAssistantException(HaErrorKind.REJECTED)
        val list = items.getValue(entityId)
        val index = list.indexOfFirst { it.uid == uid }
        if (index < 0) throw HomeAssistantException(HaErrorKind.REJECTED)
        val current = list[index]
        list[index] =
            current.copy(
                summary = summary ?: current.summary,
                completed = completed ?: current.completed,
                description = if (sendDescription) description else current.description,
            )
    }

    override suspend fun removeItem(
        credentials: HaCredentials,
        entityId: String,
        uid: String,
    ) {
        check()
        failingRemovals?.let { throw HomeAssistantException(it) }
        items[entityId]?.removeAll { it.uid == uid }
    }

    override suspend fun createList(
        credentials: HaCredentials,
        name: String,
    ): HaCreatedList {
        check()
        failingListCreations?.let { throw HomeAssistantException(it) }
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
        failingDeletions?.let { throw HomeAssistantException(it) }
        deletedEntries += configEntryId
        lists.remove(configEntryId.removePrefix("entry-"))
    }
}
