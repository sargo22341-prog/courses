package org.opensources.courses.feature.homeassistant.data.sync

import org.opensources.courses.feature.homeassistant.domain.HaCredentials
import org.opensources.courses.feature.homeassistant.domain.HaEntityRegistry
import org.opensources.courses.feature.homeassistant.domain.HaErrorKind
import org.opensources.courses.feature.homeassistant.domain.HaItemFormat
import org.opensources.courses.feature.homeassistant.domain.HaTodoList
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantException

/**
 * The [HaItemFormat] of Home Assistant lists. Only a list without description may be a Mealie list:
 * the entity registry tells, once per list for good ([SyncLocalStore.saveIntegrations]), since an
 * entity keeps its integration. A Home Assistant that cannot tell (command unknown) is not asked
 * again either. When the registry cannot be reached (server gone meanwhile, WebSocket blocked by a
 * proxy), the list is read without Mealie support for this synchronisation only, and the registry is
 * asked again at the next one.
 */
class HaListFormats(
    private val registry: HaEntityRegistry,
    private val store: SyncLocalStore,
) {
    suspend fun of(
        credentials: HaCredentials,
        lists: Collection<HaTodoList>,
    ): Map<String, HaItemFormat> {
        val candidates = lists.filter { !it.supportsDescription && it.isAvailable }.map { it.entityId }.distinct()
        val known = store.knownIntegrations(candidates)
        val unknown = candidates.filterNot { it in known }
        val integrations = if (unknown.isEmpty()) known else known + detect(credentials, unknown)
        return lists.associate { it.entityId to HaItemFormat.of(it, integrations[it.entityId]) }
    }

    private suspend fun detect(
        credentials: HaCredentials,
        entityIds: List<String>,
    ): Map<String, String?> {
        val detected =
            try {
                registry.integrations(credentials, entityIds)
            } catch (exception: HomeAssistantException) {
                // Not reached this time: asked again at the next synchronisation.
                if (exception.kind != HaErrorKind.REJECTED) return emptyMap()
                // Home Assistant answered without telling: asking again would get the same answer.
                emptyMap()
            }
        return entityIds.associateWith { detected[it] }.also { store.saveIntegrations(it) }
    }
}
