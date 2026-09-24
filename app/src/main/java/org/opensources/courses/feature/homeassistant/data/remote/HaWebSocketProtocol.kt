package org.opensources.courses.feature.homeassistant.data.remote

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.Request
import org.opensources.courses.feature.homeassistant.domain.HaCredentials
import org.opensources.courses.feature.homeassistant.domain.HaErrorKind
import org.opensources.courses.feature.homeassistant.domain.HomeAssistantException

/**
 * What every connection to the Home Assistant WebSocket API (`/api/websocket`) shares: the address,
 * the `auth` message (the only one carrying the token) and the reading of frames.
 */
internal object HaWebSocketProtocol {
    const val NORMAL_CLOSURE = 1000

    fun request(credentials: HaCredentials): Request =
        try {
            Request.Builder().url(credentials.baseUrl.trimEnd('/') + WEBSOCKET_PATH).build()
        } catch (exception: IllegalArgumentException) {
            throw HomeAssistantException(HaErrorKind.INVALID_URL, exception)
        }

    fun authMessage(token: String): String =
        buildJsonObject {
            put("type", "auth")
            put("access_token", token)
        }.toString()

    /** A frame that is not a JSON object carries nothing the app waits for: it is ignored. */
    fun parse(
        json: Json,
        text: String,
    ): JsonObject? =
        try {
            json.parseToJsonElement(text).jsonObject
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

    private const val WEBSOCKET_PATH = "/api/websocket"
}
