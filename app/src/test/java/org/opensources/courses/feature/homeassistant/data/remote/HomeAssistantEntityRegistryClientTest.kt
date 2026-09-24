package org.opensources.courses.feature.homeassistant.data.remote

import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.opensources.courses.feature.homeassistant.domain.HaCredentials
import org.opensources.courses.feature.homeassistant.domain.HaErrorKind
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantException
import java.util.concurrent.CopyOnWriteArrayList

class HomeAssistantEntityRegistryClientTest {
    private val server = MockWebServer()
    private val received = CopyOnWriteArrayList<String>()
    private val client = HomeAssistantEntityRegistryClient(Lazy { OkHttpClient() }, Json { ignoreUnknownKeys = true })

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun credentials() = HaCredentials(server.url("/").toString().trimEnd('/'), "secret-token")

    /** Behaves like Home Assistant 2026.9: auth handshake, then [answer] to `get_entries`. */
    private fun homeAssistant(answer: String) =
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
                    text.contains("config/entity_registry/get_entries") -> webSocket.send(answer)
                    text.contains("\"auth\"") -> webSocket.send("""{"type":"auth_ok","ha_version":"2026.9.2"}""")
                }
            }

            override fun onClosing(
                webSocket: WebSocket,
                code: Int,
                reason: String,
            ) {
                webSocket.close(code, null)
            }
        }

    private suspend fun ask(vararg entityIds: String) = withContext(Dispatchers.Default) { client.integrations(credentials(), entityIds.toList()) }

    @Test
    fun `tells the integration of each list, null for an entity without registry entry`() =
        runTest {
            val answer =
                """{"id":1,"type":"result","success":true,"result":{""" +
                    """"todo.mealie_courses":{"entity_id":"todo.mealie_courses","platform":"mealie","name":null},""" +
                    """"todo.shopping_list":{"entity_id":"todo.shopping_list","platform":"shopping_list"},""" +
                    """"todo.legacy":null}}"""
            server.enqueue(MockResponse.Builder().webSocketUpgrade(homeAssistant(answer)).build())

            val integrations = ask("todo.mealie_courses", "todo.shopping_list", "todo.legacy")

            assertEquals(mapOf("todo.mealie_courses" to "mealie", "todo.shopping_list" to "shopping_list", "todo.legacy" to null), integrations)
            assertEquals("/api/websocket", server.takeRequest().url.encodedPath)
            assertTrue(received.toString(), received.any { it.contains("\"access_token\":\"secret-token\"") })
            assertTrue(
                received.toString(),
                received.any { it.contains("\"entity_ids\":[\"todo.mealie_courses\",\"todo.shopping_list\",\"todo.legacy\"]") },
            )
        }

    @Test
    fun `a command unknown to Home Assistant is a refusal`() =
        runTest {
            val answer = """{"id":1,"type":"result","success":false,"error":{"code":"unknown_command","message":"Unknown command."}}"""
            server.enqueue(MockResponse.Builder().webSocketUpgrade(homeAssistant(answer)).build())

            assertFails(HaErrorKind.REJECTED) { ask("todo.mealie_courses") }
        }

    @Test
    fun `a refused token is reported as such`() =
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
                    }

                    override fun onClosing(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        webSocket.close(code, null)
                    }
                }
            server.enqueue(MockResponse.Builder().webSocketUpgrade(refusing).build())

            assertFails(HaErrorKind.UNAUTHORIZED) { ask("todo.mealie_courses") }
        }

    @Test
    fun `a server that is gone is unreachable`() =
        runTest {
            val credentials = credentials()
            server.close()

            assertFails(HaErrorKind.UNREACHABLE) {
                withContext(Dispatchers.Default) { client.integrations(credentials, listOf("todo.mealie_courses")) }
            }
        }

    @Test
    fun `nothing asked opens no connection`() =
        runTest {
            assertEquals(emptyMap<String, String?>(), client.integrations(credentials(), emptyList()))
            assertEquals(0, server.requestCount)
        }

    private suspend fun assertFails(
        kind: HaErrorKind,
        block: suspend () -> Unit,
    ) {
        try {
            block()
            fail("expected $kind")
        } catch (exception: HomeAssistantException) {
            assertEquals(kind, exception.kind)
        }
    }
}
