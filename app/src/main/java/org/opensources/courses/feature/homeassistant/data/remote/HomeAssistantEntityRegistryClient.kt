package org.opensources.courses.feature.homeassistant.data.remote

import dagger.Lazy
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.opensources.courses.feature.homeassistant.data.remote.HaWebSocketProtocol.string
import org.opensources.courses.feature.homeassistant.domain.HaCredentials
import org.opensources.courses.feature.homeassistant.domain.HaEntityRegistry
import org.opensources.courses.feature.homeassistant.domain.HaErrorKind
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantException
import javax.inject.Inject

/**
 * The entity registry through the WebSocket API (`config/entity_registry/get_entries`, which needs
 * no administrator rights): the REST API does not tell which integration provides an entity. One
 * short connection per question, closed as soon as Home Assistant answered.
 */
class HomeAssistantEntityRegistryClient
    @Inject
    constructor(
        private val client: Lazy<OkHttpClient>,
        private val json: Json,
    ) : HaEntityRegistry {
        override suspend fun integrations(
            credentials: HaCredentials,
            entityIds: Collection<String>,
        ): Map<String, String?> {
            if (entityIds.isEmpty()) return emptyMap()
            val asked = entityIds.distinct()
            return withTimeoutOrNull(TIMEOUT_MILLIS) { ask(credentials, asked).first() }
                ?: throw HomeAssistantException(HaErrorKind.UNREACHABLE)
        }

        private fun ask(
            credentials: HaCredentials,
            entityIds: List<String>,
        ): Flow<Map<String, String?>> =
            callbackFlow {
                val listener =
                    object : WebSocketListener() {
                        override fun onMessage(
                            webSocket: WebSocket,
                            text: String,
                        ) {
                            val message = HaWebSocketProtocol.parse(json, text) ?: return
                            when (message.string("type")) {
                                "auth_required" -> webSocket.send(HaWebSocketProtocol.authMessage(credentials.token))
                                "auth_ok" -> webSocket.send(getEntriesMessage(entityIds))
                                "auth_invalid" -> close(HomeAssistantException(HaErrorKind.UNAUTHORIZED))
                                "result" -> if ((message["id"] as? JsonPrimitive)?.intOrNull == REQUEST_ID) answer(message)
                            }
                        }

                        private fun answer(result: JsonObject) {
                            if ((result["success"] as? JsonPrimitive)?.booleanOrNull != true) {
                                close(HomeAssistantException(HaErrorKind.REJECTED))
                                return
                            }
                            val entries = result["result"] as? JsonObject
                            trySend(entityIds.associateWith { entityId -> (entries?.get(entityId) as? JsonObject)?.string("platform") })
                            close()
                        }

                        override fun onClosing(
                            webSocket: WebSocket,
                            code: Int,
                            reason: String,
                        ) {
                            close(HomeAssistantException(HaErrorKind.UNREACHABLE))
                        }

                        override fun onFailure(
                            webSocket: WebSocket,
                            t: Throwable,
                            response: Response?,
                        ) {
                            close(HomeAssistantException(HaErrorKind.UNREACHABLE, t))
                        }
                    }
                val socket = client.get().newWebSocket(HaWebSocketProtocol.request(credentials), listener)
                awaitClose { socket.close(HaWebSocketProtocol.NORMAL_CLOSURE, null) }
            }

        private fun getEntriesMessage(entityIds: List<String>): String =
            buildJsonObject {
                put("id", REQUEST_ID)
                put("type", "config/entity_registry/get_entries")
                putJsonArray("entity_ids") { entityIds.forEach { add(JsonPrimitive(it)) } }
            }.toString()

        private companion object {
            const val REQUEST_ID = 1
            const val TIMEOUT_MILLIS = 10_000L
        }
    }
