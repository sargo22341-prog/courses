package org.opensources.courses.feature.homeassistant.data.remote

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.opensources.courses.feature.homeassistant.domain.HaCredentials
import org.opensources.courses.feature.homeassistant.domain.HaErrorKind
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class HomeAssistantClientTest {
    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var client: HomeAssistantClient
    private lateinit var credentials: HaCredentials

    @Before
    fun setUp() {
        server.start()
        val api =
            Retrofit
                .Builder()
                .baseUrl("http://localhost/")
                .client(OkHttpClient())
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(HomeAssistantApi::class.java)
        client = HomeAssistantClient(api, json)
        credentials = HaCredentials(server.url("/").toString().trimEnd('/'), "secret-token")
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun respond(
        body: String,
        code: Int = 200,
    ) {
        server.enqueue(MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build())
    }

    @Test
    fun `lists only todo entities with their capabilities`() =
        runTest {
            respond(
                """
                [
                  {"entity_id":"light.kitchen","state":"on","attributes":{}},
                  {"entity_id":"todo.shopping_list","state":"2","attributes":{"friendly_name":"Shopping List","supported_features":15}},
                  {"entity_id":"todo.courses","state":"0","attributes":{"friendly_name":"Courses","supported_features":127}}
                ]
                """.trimIndent(),
            )

            val lists = client.getTodoLists(credentials)

            assertEquals(listOf("todo.courses", "todo.shopping_list"), lists.map { it.entityId })
            assertTrue(lists[0].supportsDescription)
            assertFalse(lists[1].supportsDescription)
            assertTrue(lists.all { it.isEditable })
            val request = server.takeRequest()
            assertEquals("/api/states", request.url.encodedPath)
            assertEquals("Bearer secret-token", request.headers["Authorization"])
        }

    @Test
    fun `a list that cannot add, change and remove items is read-only`() =
        runTest {
            respond(
                """
                [
                  {"entity_id":"todo.lecture","state":"3","attributes":{"friendly_name":"Lecture","supported_features":1}},
                  {"entity_id":"todo.sans_suppression","state":"0","attributes":{"friendly_name":"Sans suppression","supported_features":5}}
                ]
                """.trimIndent(),
            )

            val lists = client.getTodoLists(credentials)

            assertTrue(lists.none { it.isEditable })
        }

    @Test
    fun `reads items through the get_items service response`() =
        runTest {
            respond(
                """
                {"changed_states":[],"service_response":{"todo.courses":{"items":[
                  {"uid":"u1","summary":"Lait","status":"needs_action","description":"2"},
                  {"uid":"u2","summary":"Pain","status":"completed"}
                ]}}}
                """.trimIndent(),
            )

            val items = client.getItems(credentials, "todo.courses")

            assertEquals(listOf("Lait", "Pain"), items.map { it.summary })
            assertEquals(listOf(false, true), items.map { it.completed })
            assertEquals("2", items[0].description)
            val request = server.takeRequest()
            assertEquals("/api/services/todo/get_items", request.url.encodedPath)
            assertEquals("return_response", request.url.encodedQuery)
        }

    @Test
    fun `update sends status and clears the description`() =
        runTest {
            respond("[]")

            client.updateItem(credentials, "todo.courses", "u1", "Lait", completed = true, description = null, sendDescription = true)

            val body = server.takeRequest().body?.utf8().orEmpty()
            assertTrue(body, body.contains("\"status\":\"completed\""))
            assertTrue(body, body.contains("\"description\":null"))
            assertTrue(body, body.contains("\"item\":\"u1\""))
        }

    @Test
    fun `unavailable lists and completion dates are read`() =
        runTest {
            respond("""[{"entity_id":"todo.mon_agenda","state":"unavailable","attributes":{"friendly_name":"Mon agenda"}}]""")
            respond(
                """
                {"service_response":{"todo.courses":{"items":[
                  {"uid":"u1","summary":"Pain","status":"completed","completed":"2026-09-13T14:10:25.772961+00:00"}
                ]}}}
                """.trimIndent(),
            )

            assertFalse(client.getTodoLists(credentials).single().isAvailable)
            assertEquals(1789308625772L, client.getItems(credentials, "todo.courses").single().completedAt)
        }

    @Test
    fun `check sent alone does not rename the item`() =
        runTest {
            respond("[]")

            client.updateItem(credentials, "todo.courses", "u1", summary = null, completed = true, description = null, sendDescription = false)

            val body = server.takeRequest().body?.utf8().orEmpty()
            assertTrue(body, body.contains("\"status\":\"completed\""))
            assertFalse(body, body.contains("rename"))
            assertFalse(body, body.contains("description"))
        }

    @Test
    fun `list created with a name already used in Home Assistant gets a number`() =
        runTest {
            respond("""[{"entity_id":"todo.courses","attributes":{"friendly_name":"Courses","supported_features":127}}]""")
            respond("""{"type":"form","flow_id":"f1"}""")
            respond("""{"type":"create_entry","flow_id":"f1","result":{"entry_id":"e1"}}""")
            respond(
                """
                [{"entity_id":"todo.courses","attributes":{"friendly_name":"Courses"}},
                 {"entity_id":"todo.courses_2","attributes":{"friendly_name":"Courses 2"}}]
                """.trimIndent(),
            )

            val created = client.createList(credentials, "Courses")

            assertEquals("todo.courses_2", created.entityId)
            assertEquals("e1", created.configEntryId)
            assertEquals("Courses 2", created.name)
            server.takeRequest()
            server.takeRequest()
            val submitted = server.takeRequest().body?.utf8().orEmpty()
            assertTrue(submitted, submitted.contains("\"todo_list_name\":\"Courses 2\""))
        }

    @Test
    fun `name refused by Local To-do is retried with the next number`() =
        runTest {
            respond("[]")
            respond("""{"type":"form","flow_id":"f1"}""")
            respond("""{"type":"abort","flow_id":"f1","reason":"already_configured"}""")
            respond("""{"type":"form","flow_id":"f2"}""")
            respond("""{"type":"create_entry","flow_id":"f2","result":{"entry_id":"e2"}}""")
            respond("""[{"entity_id":"todo.courses_2","attributes":{"friendly_name":"Courses 2"}}]""")

            val created = client.createList(credentials, "Courses")

            assertEquals("Courses 2", created.name)
            assertEquals("todo.courses_2", created.entityId)
        }

    @Test
    fun `refused token is reported as unauthorized`() =
        runTest {
            respond("""{"message":"401: Unauthorized"}""", code = 401)

            assertErrorKind(HaErrorKind.UNAUTHORIZED) { client.testConnection(credentials) }
        }

    @Test
    fun `stopped server is reported as unreachable`() =
        runTest {
            server.close()

            assertErrorKind(HaErrorKind.UNREACHABLE) { client.testConnection(credentials) }
        }

    @Test
    fun `malformed address is reported as invalid before any request`() =
        runTest {
            assertErrorKind(HaErrorKind.INVALID_URL) { client.testConnection(HaCredentials("https://ha nas.home", "secret-token")) }
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `Retrofit refusing a method is not blamed on the address`() =
        runTest {
            // What a release build did when R8 had removed ApiStatusDto: "Adresse invalide" for a valid address.
            val client = HomeAssistantClient(ConverterlessApi(), json)

            assertErrorKind(HaErrorKind.PROTOCOL) { client.testConnection(credentials) }
        }

    private class ConverterlessApi : HomeAssistantApi {
        private fun refuse(): Nothing = throw IllegalArgumentException("Unable to create converter for class java.lang.Object")

        override suspend fun apiStatus(
            url: String,
            authorization: String,
        ): ApiStatusDto = refuse()

        override suspend fun states(
            url: String,
            authorization: String,
        ): List<EntityStateDto> = refuse()

        override suspend fun callService(
            url: String,
            authorization: String,
            body: JsonObject,
        ): JsonElement = refuse()

        override suspend fun configFlow(
            url: String,
            authorization: String,
            body: JsonObject,
        ): ConfigFlowDto = refuse()

        override suspend fun deleteConfigEntry(
            url: String,
            authorization: String,
        ): JsonElement = refuse()
    }

    private suspend fun assertErrorKind(
        expected: HaErrorKind,
        block: suspend () -> Unit,
    ) {
        try {
            block()
            fail("Expected $expected")
        } catch (exception: HomeAssistantException) {
            assertEquals(expected, exception.kind)
        }
    }
}
