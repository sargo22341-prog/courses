package org.opensources.courses.feature.homeassistant.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class ApiStatusDto(
    val message: String? = null,
)

@Serializable
data class EntityStateDto(
    @SerialName("entity_id") val entityId: String,
    val state: String? = null,
    val attributes: JsonObject = JsonObject(emptyMap()),
)

/** Response of `todo.get_items` called with `?return_response`. */
@Serializable
data class ServiceResponseDto(
    @SerialName("service_response") val serviceResponse: Map<String, TodoItemsDto> = emptyMap(),
)

@Serializable
data class TodoItemsDto(
    val items: List<TodoItemDto> = emptyList(),
)

@Serializable
data class TodoItemDto(
    val uid: String,
    val summary: String = "",
    val status: String = STATUS_NEEDS_ACTION,
    val description: String? = null,
) {
    companion object {
        const val STATUS_NEEDS_ACTION = "needs_action"
        const val STATUS_COMPLETED = "completed"
    }
}

@Serializable
data class ConfigFlowDto(
    val type: String? = null,
    @SerialName("flow_id") val flowId: String? = null,
    val result: JsonElement? = null,
    /** Why the flow aborted, e.g. `already_configured` when the list name is taken. */
    val reason: String? = null,
)
