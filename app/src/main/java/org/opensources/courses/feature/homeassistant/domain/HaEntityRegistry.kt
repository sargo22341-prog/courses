package org.opensources.courses.feature.homeassistant.domain

/** The Home Assistant entity registry, which tells the integration behind an entity. */
interface HaEntityRegistry {
    /**
     * Integration of each of [entityIds] (`mealie`, `local_todo`…), null for an entity the registry
     * does not hold. Throws [HomeAssistantException]: [HaErrorKind.REJECTED] when Home Assistant
     * answered without telling (command unknown to an old version).
     */
    suspend fun integrations(
        credentials: HaCredentials,
        entityIds: Collection<String>,
    ): Map<String, String?>
}
