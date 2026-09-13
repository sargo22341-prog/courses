package org.opensources.courses.feature.catalog.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Streaming

/**
 * OpenFoodFacts publishes its taxonomies as static files on a CDN (about 1.8 MB gzipped for
 * categories). One download per week replaces thousands of per-keystroke API calls, and no
 * personal data is ever sent: the request carries only the User-Agent and an optional ETag.
 */
interface OpenFoodFactsApi {
    @Streaming
    @GET("data/taxonomies/categories.json")
    suspend fun downloadCategories(
        @Header("If-None-Match") etag: String?,
    ): Response<ResponseBody>

    companion object {
        const val BASE_URL = "https://static.openfoodfacts.org/"
    }
}
