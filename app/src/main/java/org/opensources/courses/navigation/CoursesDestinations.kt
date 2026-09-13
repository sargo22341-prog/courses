package org.opensources.courses.navigation

import kotlinx.serialization.Serializable

@Serializable
data object WelcomeDestination

/** [listId] null shows the default list. */
@Serializable
data class ShoppingDestination(
    val listId: String? = null,
)

@Serializable
data object ListsDestination

@Serializable
data object SettingsDestination

@Serializable
data object HomeAssistantDestination
