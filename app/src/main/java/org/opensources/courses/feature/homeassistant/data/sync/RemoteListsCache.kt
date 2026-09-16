package org.opensources.courses.feature.homeassistant.data.sync

import org.opensources.courses.feature.homeassistant.domain.HaTodoList

/**
 * The Home Assistant lists read by the last successful synchronisation. Reading them downloads the
 * state of every entity of the installation (`/api/states`), while a change of items, the most
 * frequent one, changes no list: those synchronisations reuse them.
 */
class RemoteListsCache {
    /** Lists are only valid for the server and the token they were read with. */
    data class Source(
        val baseUrl: String,
        val tokenVersion: Int,
    )

    private class Entry(
        val source: Source,
        val lists: Map<String, HaTodoList>,
    )

    @Volatile
    private var entry: Entry? = null

    /**
     * The lists read from [source], by entity id, if they include every entity of [linkedEntityIds]:
     * a linked list missing from them may be new, so the lists must be read again.
     */
    fun get(
        source: Source,
        linkedEntityIds: Collection<String>,
    ): Map<String, HaTodoList>? = entry?.takeIf { it.source == source && it.lists.keys.containsAll(linkedEntityIds) }?.lists

    fun put(
        source: Source,
        lists: Map<String, HaTodoList>,
    ) {
        entry = Entry(source, lists)
    }

    fun clear() {
        entry = null
    }
}
