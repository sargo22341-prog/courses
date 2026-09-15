package org.opensources.courses.feature.homeassistant.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.opensources.courses.feature.homeassistant.domain.HaCreatedList
import org.opensources.courses.feature.homeassistant.domain.HaCredentials
import org.opensources.courses.feature.homeassistant.domain.HaErrorKind
import org.opensources.courses.feature.homeassistant.domain.HaListNameAllocator
import org.opensources.courses.feature.homeassistant.domain.HaTodoItem
import org.opensources.courses.feature.homeassistant.domain.HaTodoList
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantException
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantGateway
import retrofit2.HttpException
import java.io.IOException
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import javax.inject.Inject

class HomeAssistantClient
    @Inject
    constructor(
        private val api: HomeAssistantApi,
        private val json: Json,
    ) : HomeAssistantGateway {
        override suspend fun testConnection(credentials: HaCredentials) {
            call { api.apiStatus(credentials.url("/api/"), credentials.bearer()) }
        }

        override suspend fun getTodoLists(credentials: HaCredentials): List<HaTodoList> =
            call { api.states(credentials.url("/api/states"), credentials.bearer()) }
                .filter { it.entityId.startsWith(TODO_DOMAIN) }
                .map { state ->
                    val features = state.attributes["supported_features"]?.jsonPrimitive?.intOrNull ?: 0
                    HaTodoList(
                        entityId = state.entityId,
                        name = state.attributes["friendly_name"]?.jsonPrimitive?.contentOrNull ?: state.entityId,
                        supportsDescription = features and FEATURE_SET_DESCRIPTION != 0,
                        isAvailable = state.state != STATE_UNAVAILABLE,
                        isEditable = features and FEATURE_EDIT_ITEMS == FEATURE_EDIT_ITEMS,
                    )
                }.sortedBy { it.name.lowercase() }

        override suspend fun getItems(
            credentials: HaCredentials,
            entityId: String,
        ): List<HaTodoItem> {
            val response =
                call {
                    api.callService(
                        credentials.url("/api/services/todo/get_items?return_response"),
                        credentials.bearer(),
                        buildJsonObject { put("entity_id", entityId) },
                    )
                }
            val decoded = call { json.decodeFromJsonElement(ServiceResponseDto.serializer(), response) }
            return decoded.serviceResponse[entityId]?.items.orEmpty().map {
                HaTodoItem(it.uid, it.summary, it.status == TodoItemDto.STATUS_COMPLETED, it.description, it.completed?.let(::epochMillis))
            }
        }

        /** An unreadable date only loses its tie-break value: the item itself is still synchronised. */
        private fun epochMillis(isoDate: String): Long? =
            try {
                OffsetDateTime.parse(isoDate).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                null
            }

        override suspend fun addItem(
            credentials: HaCredentials,
            entityId: String,
            summary: String,
            description: String?,
        ) {
            service(
                credentials,
                "add_item",
                buildJsonObject {
                    put("entity_id", entityId)
                    put("item", summary)
                    if (description != null) put("description", description)
                },
            )
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
            service(
                credentials,
                "update_item",
                buildJsonObject {
                    put("entity_id", entityId)
                    put("item", uid)
                    summary?.let { put("rename", it) }
                    completed?.let { put("status", if (it) TodoItemDto.STATUS_COMPLETED else TodoItemDto.STATUS_NEEDS_ACTION) }
                    if (sendDescription) put("description", description?.let(::JsonPrimitive) ?: JsonNull)
                },
            )
        }

        override suspend fun removeItem(
            credentials: HaCredentials,
            entityId: String,
            uid: String,
        ) {
            service(credentials, "remove_item", buildJsonObject {
                put("entity_id", entityId)
                put("item", uid)
            })
        }

        override suspend fun createList(
            credentials: HaCredentials,
            name: String,
        ): HaCreatedList {
            val lists = getTodoLists(credentials)
            val existing = lists.map { it.entityId }.toSet()
            val takenNames = lists.map { it.name }.toMutableSet()
            repeat(CREATE_ATTEMPTS) {
                val candidate = HaListNameAllocator.uniqueName(name, takenNames)
                val created = submitLocalTodoFlow(credentials, candidate)
                if (created.type == FLOW_ABORT && created.reason == ALREADY_CONFIGURED) {
                    // A Local To-do list without a visible entity (disabled…) already uses this name.
                    takenNames += candidate
                    return@repeat
                }
                if (created.type != FLOW_CREATE_ENTRY) throw HomeAssistantException(HaErrorKind.REJECTED)
                val entryId = (created.result as? JsonObject)?.get("entry_id")?.jsonPrimitive?.contentOrNull
                return HaCreatedList(awaitNewEntity(credentials, existing), entryId, candidate)
            }
            throw HomeAssistantException(HaErrorKind.REJECTED)
        }

        private suspend fun submitLocalTodoFlow(
            credentials: HaCredentials,
            name: String,
        ): ConfigFlowDto {
            val form =
                call {
                    api.configFlow(
                        credentials.url("/api/config/config_entries/flow"),
                        credentials.bearer(),
                        buildJsonObject {
                            put("handler", LOCAL_TODO_HANDLER)
                            put("show_advanced_options", false)
                        },
                    )
                }
            val flowId = form.flowId ?: throw HomeAssistantException(HaErrorKind.PROTOCOL)
            return call {
                api.configFlow(
                    credentials.url("/api/config/config_entries/flow/$flowId"),
                    credentials.bearer(),
                    buildJsonObject { put("todo_list_name", name) },
                )
            }
        }

        /** The entity is registered asynchronously after the config entry is created. */
        private suspend fun awaitNewEntity(
            credentials: HaCredentials,
            existing: Set<String>,
        ): String {
            repeat(ENTITY_POLL_ATTEMPTS) {
                getTodoLists(credentials).firstOrNull { it.entityId !in existing }?.let { return it.entityId }
                delay(ENTITY_POLL_DELAY_MILLIS)
            }
            throw HomeAssistantException(HaErrorKind.PROTOCOL)
        }

        override suspend fun deleteList(
            credentials: HaCredentials,
            configEntryId: String,
        ) {
            call { api.deleteConfigEntry(credentials.url("/api/config/config_entries/entry/$configEntryId"), credentials.bearer()) }
        }

        private suspend fun service(
            credentials: HaCredentials,
            service: String,
            body: JsonObject,
        ) {
            call { api.callService(credentials.url("/api/services/todo/$service"), credentials.bearer(), body) }
        }

        private suspend fun <T> call(block: suspend () -> T): T =
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: HomeAssistantException) {
                throw exception
            } catch (exception: HttpException) {
                throw HomeAssistantException(kindForStatus(exception.code()), exception)
            } catch (exception: IOException) {
                throw HomeAssistantException(HaErrorKind.UNREACHABLE, exception)
            } catch (exception: SerializationException) {
                throw HomeAssistantException(HaErrorKind.PROTOCOL, exception)
            } catch (exception: IllegalArgumentException) {
                // Addresses are checked by url() before any call, so this is Retrofit refusing a method
                // of HomeAssistantApi (a build problem, e.g. R8 removing a response class), not the
                // address typed by the user: it must never be reported as an invalid address.
                throw HomeAssistantException(HaErrorKind.PROTOCOL, exception)
            }

        private fun kindForStatus(code: Int): HaErrorKind =
            when (code) {
                401, 403 -> HaErrorKind.UNAUTHORIZED
                404 -> HaErrorKind.NOT_FOUND
                400 -> HaErrorKind.REJECTED
                else -> HaErrorKind.PROTOCOL
            }

        /** A malformed address is reported as such before Retrofit or OkHttp see it. */
        private fun HaCredentials.url(path: String): String {
            val url = baseUrl.trimEnd('/') + path
            if (url.toHttpUrlOrNull() == null) throw HomeAssistantException(HaErrorKind.INVALID_URL)
            return url
        }

        private fun HaCredentials.bearer(): String = "Bearer $token"

        private companion object {
            const val TODO_DOMAIN = "todo."
            const val STATE_UNAVAILABLE = "unavailable"
            const val FEATURE_SET_DESCRIPTION = 64

            /** `TodoListEntityFeature` CREATE (1), DELETE (2) and UPDATE (4) items. */
            const val FEATURE_EDIT_ITEMS = 7
            const val LOCAL_TODO_HANDLER = "local_todo"
            const val FLOW_CREATE_ENTRY = "create_entry"
            const val FLOW_ABORT = "abort"
            const val ALREADY_CONFIGURED = "already_configured"
            const val CREATE_ATTEMPTS = 5
            const val ENTITY_POLL_ATTEMPTS = 6
            const val ENTITY_POLL_DELAY_MILLIS = 500L
        }
    }
