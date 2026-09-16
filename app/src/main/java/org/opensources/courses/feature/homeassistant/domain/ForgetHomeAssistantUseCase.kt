package org.opensources.courses.feature.homeassistant.domain

import org.opensources.courses.core.sync.SyncCoordinator
import javax.inject.Inject

/**
 * "Oublier la connexion" (lent or sold phone, token revoked): the token and the Home Assistant
 * settings are erased, and the lists stay on this phone without any link. Runs while no
 * synchronisation does, so none can link a list again meanwhile. Nothing is deleted in Home Assistant.
 */
class ForgetHomeAssistantUseCase
    @Inject
    constructor(
        private val config: HaConfigRepository,
        private val links: HaListLinkRepository,
        private val sync: SyncCoordinator,
    ) {
        suspend operator fun invoke() =
            sync.withoutSynchronisation {
                config.forgetConnection()
                links.unlinkAll()
            }
    }
