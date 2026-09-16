package org.opensources.courses.feature.homeassistant.data.remote

import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.opensources.courses.core.sync.RemoteChange
import org.opensources.courses.feature.homeassistant.domain.HaCredentials
import java.util.concurrent.CopyOnWriteArrayList

class HomeAssistantWebSocketClientTest {
    private val server = MockWebServer()
    private val received = CopyOnWriteArrayList<String>()
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HomeAssistantWebSocketClient(Lazy { OkHttpClient() }, json)

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    /** Behaves like Home Assistant 2026.9: auth handshake, then one event per subscription, with its id. */
    private val homeAssistant =
        object : WebSocketListener() {
            override fun onOpen(
                webSocket: WebSocket,
                response: Response,
            ) {
                webSocket.send("""{"type":"auth_required","ha_version":"2026.9.2"}""")
            }

            override fun onMessage(
                webSocket: WebSocket,
                text: String,
            ) {
                received += text
                when {
                    text.contains("todo/item/subscribe") -> {
                        val id = json.parseToJsonElement(text).jsonObject.getValue("id").jsonPrimitive.int
                        webSocket.send("""{"id":$id,"type":"result","success":true,"result":null}""")
                        webSocket.send("""{"id":$id,"type":"event","event":{"items":[]}}""")
                    }
                    text.contains("\"auth\"") -> webSocket.send("""{"type":"auth_ok"}""")
                }
            }

            // Answers the client's close so the server can shut down.
            override fun onClosing(
                webSocket: WebSocket,
                code: Int,
                reason: String,
            ) {
                webSocket.close(code, null)
            }
        }

    @Test
    fun `authenticates, subscribes to each linked list and signals its events`() =
        runTest {
            server.enqueue(MockResponse.Builder().webSocketUpgrade(homeAssistant).build())
            val credentials = HaCredentials(server.url("/").toString().trimEnd('/'), "secret-token")

            val change =
                withContext(Dispatchers.Default) {
                    withTimeout(TIMEOUT_MILLIS) { client.observeItemChanges(credentials, setOf("todo.courses")).first() }
                }

            assertEquals(RemoteChange.ItemsChanged(setOf("todo.courses")), change)
            assertEquals("/api/websocket", server.takeRequest().url.encodedPath)
            assertTrue(received.toString(), received.any { it.contains("\"access_token\":\"secret-token\"") })
            assertTrue(received.toString(), received.any { it.contains("\"entity_id\":\"todo.courses\"") })
        }

    @Test
    fun `a refused token is signalled once and never tried again`() =
        runTest {
            val refusing =
                object : WebSocketListener() {
                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response,
                    ) {
                        webSocket.send("""{"type":"auth_required","ha_version":"2026.9.2"}""")
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        webSocket.send("""{"type":"auth_invalid","message":"Invalid access token or password"}""")
                        webSocket.close(NORMAL_CLOSURE, null)
                    }
                }
            // A reconnection would find a second upgrade and succeed: the test would then time out.
            server.enqueue(MockResponse.Builder().webSocketUpgrade(refusing).build())
            server.enqueue(MockResponse.Builder().webSocketUpgrade(homeAssistant).build())
            val credentials = HaCredentials(server.url("/").toString().trimEnd('/'), "revoked-token")

            val emitted =
                withContext(Dispatchers.Default) {
                    withTimeout(TIMEOUT_MILLIS) { client.observeItemChanges(credentials, setOf("todo.courses")).toList() }
                }

            assertEquals(listOf(RemoteChange.Refused), emitted)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `each event names the list of its subscription`() =
        runTest {
            server.enqueue(MockResponse.Builder().webSocketUpgrade(homeAssistant).build())
            val credentials = HaCredentials(server.url("/").toString().trimEnd('/'), "secret-token")
            val lists = setOf("todo.courses", "todo.bricolage")

            val changed =
                withContext(Dispatchers.Default) {
                    withTimeout(TIMEOUT_MILLIS) {
                        client
                            .observeItemChanges(credentials, lists)
                            .filterIsInstance<RemoteChange.ItemsChanged>()
                            .take(lists.size)
                            .toList()
                    }
                }

            assertEquals(lists, changed.flatMap { it.remoteListIds }.toSet())
            assertTrue(changed.toString(), changed.all { it.remoteListIds.size == 1 })
        }

    @Test
    fun `a lost connection is signalled before it is opened again`() =
        runTest {
            val closing =
                object : WebSocketListener() {
                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response,
                    ) {
                        webSocket.close(GOING_AWAY, null)
                    }
                }
            server.enqueue(MockResponse.Builder().webSocketUpgrade(closing).build())
            val credentials = HaCredentials(server.url("/").toString().trimEnd('/'), "secret-token")

            val change =
                withContext(Dispatchers.Default) {
                    withTimeout(TIMEOUT_MILLIS) { client.observeItemChanges(credentials, setOf("todo.courses")).first() }
                }

            assertEquals(RemoteChange.Disconnected, change)
        }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val NORMAL_CLOSURE = 1000
        const val GOING_AWAY = 1001
    }
}
