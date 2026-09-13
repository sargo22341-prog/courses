package org.opensources.courses.feature.homeassistant.data.remote

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Home Assistant REST API. The server address is chosen by the user, so every call takes an
 * absolute [Url]; the token travels only in the `Authorization` header.
 */
interface HomeAssistantApi {
    @GET
    suspend fun apiStatus(
        @Url url: String,
        @Header("Authorization") authorization: String,
    ): ApiStatusDto

    @GET
    suspend fun states(
        @Url url: String,
        @Header("Authorization") authorization: String,
    ): List<EntityStateDto>

    @POST
    suspend fun callService(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body body: JsonObject,
    ): JsonElement

    @POST
    suspend fun configFlow(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body body: JsonObject,
    ): ConfigFlowDto

    @DELETE
    suspend fun deleteConfigEntry(
        @Url url: String,
        @Header("Authorization") authorization: String,
    ): JsonElement
}
