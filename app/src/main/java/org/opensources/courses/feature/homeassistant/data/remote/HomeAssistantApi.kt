package org.opensources.courses.feature.homeassistant.data.remote

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

    /** `todo.get_items` called with `?return_response`. */
    @POST
    suspend fun getItems(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body body: JsonObject,
    ): ServiceResponseDto

    /** A service whose answer (the entities it changed) is not needed: it is not decoded. */
    @POST
    suspend fun callService(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body body: JsonObject,
    )

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
    )
}
