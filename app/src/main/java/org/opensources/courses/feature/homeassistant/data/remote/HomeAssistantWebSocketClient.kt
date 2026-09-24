package org.opensources.courses.feature.homeassistant.data.remote

import dagger.Lazy
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.opensources.courses.core.sync.RemoteChange
import org.opensources.courses.feature.homeassistant.data.remote.HaWebSocketProtocol.string
import org.opensources.courses.feature.homeassistant.domain.HaCredentials
import org.opensources.courses.feature.homeassistant.domain.HaErrorKind
import org.opensources.courses.feature.homeassistant.domain.HaLiveUpdates
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Live to-do changes through the Home Assistant WebSocket API (`todo/item/subscribe`).
 *
 * An event only tells which list changed: the synchronisation engine then reads and merges that list
 * as usual, so there is a single merge path. Each event carries the id of its subscription, hence of
 * its list. The token travels only in the `auth` message. A lost connection is reopened after a
 * growing delay (5 s up to 5 min); a refused token is never tried again.
 */
class HomeAssistantWebSocketClient
    @Inject
    constructor(
        client: Lazy<OkHttpClient>,
        private val json: Json,
    ) : HaLiveUpdates {
        // Built on first use, not at start. No read timeout on an idle connection; pings detect a dead one.
        private val socketClient by lazy {
            client
                .get()
                .newBuilder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
                .build()
        }

        override fun observeItemChanges(
            credentials: HaCredentials,
            entityIds: Set<String>,
        ): Flow<RemoteChange> =
            flow {
                var failures = 0
                while (true) {
                    var lost = true
                    emitAll(
                        connect(credentials, entityIds)
                            .onEach { failures = 0 }
                            .catch { cause ->
                                if (cause !is HomeAssistantException) throw cause
                                lost = cause.kind == HaErrorKind.UNREACHABLE
                            },
                    )
                    if (!lost) {
                        // Retrying cannot help (refused token, invalid address): the synchronisation
                        // asked here reports it, and the connection is opened again once they change.
                        emit(RemoteChange.Refused)
                        return@flow
                    }
                    emit(RemoteChange.Disconnected)
                    delay(retryDelayMillis(failures++))
                }
            }

        private fun connect(
            credentials: HaCredentials,
            entityIds: Set<String>,
        ): Flow<RemoteChange> {
            val entityBySubscription = entityIds.withIndex().associate { (index, entityId) -> index + FIRST_SUBSCRIPTION_ID to entityId }
            // Every event is kept, since each names a list to synchronise; they are few and handled at once.
            return callbackFlow {
                val listener =
                    object : WebSocketListener() {
                        override fun onMessage(
                            webSocket: WebSocket,
                            text: String,
                        ) {
                            val message = HaWebSocketProtocol.parse(json, text) ?: return
                            when (message.string("type")) {
                                "auth_required" -> webSocket.send(HaWebSocketProtocol.authMessage(credentials.token))
                                "auth_ok" -> entityBySubscription.forEach { (id, entityId) -> webSocket.send(subscribeMessage(id, entityId)) }
                                "auth_invalid" -> close(HomeAssistantException(HaErrorKind.UNAUTHORIZED))
                                "event" -> trySend(RemoteChange.ItemsChanged(changedLists(message, entityBySubscription)))
                            }
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
                val socket = socketClient.newWebSocket(HaWebSocketProtocol.request(credentials), listener)
                awaitClose { socket.close(HaWebSocketProtocol.NORMAL_CLOSURE, null) }
            }.buffer(Channel.UNLIMITED)
        }

        /** The list of the event's subscription; every list when the event does not tell. */
        private fun changedLists(
            event: JsonObject,
            entityBySubscription: Map<Int, String>,
        ): Set<String> {
            val entityId = (event["id"] as? JsonPrimitive)?.intOrNull?.let(entityBySubscription::get)
            return if (entityId != null) setOf(entityId) else entityBySubscription.values.toSet()
        }

        private fun subscribeMessage(
            id: Int,
            entityId: String,
        ): String =
            buildJsonObject {
                put("id", id)
                put("type", "todo/item/subscribe")
                put("entity_id", entityId)
            }.toString()

        private fun retryDelayMillis(failures: Int): Long = (FIRST_RETRY_MILLIS shl failures.coerceAtMost(MAX_BACKOFF_STEPS)).coerceAtMost(MAX_RETRY_MILLIS)

        private companion object {
            const val FIRST_SUBSCRIPTION_ID = 1
            const val PING_INTERVAL_SECONDS = 30L
            const val FIRST_RETRY_MILLIS = 5_000L
            const val MAX_RETRY_MILLIS = 5 * 60 * 1_000L
            const val MAX_BACKOFF_STEPS = 6
        }
    }
