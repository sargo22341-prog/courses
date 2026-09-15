package org.opensources.courses.feature.catalog.data.remote

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.opensources.courses.core.common.IoDispatcher
import org.opensources.courses.feature.catalog.domain.CatalogDownloadException
import org.opensources.courses.feature.catalog.domain.CatalogRemoteSource
import org.opensources.courses.feature.catalog.domain.RemoteCatalogResult
import java.io.IOException
import java.net.HttpURLConnection
import javax.inject.Inject

class OpenFoodFactsCatalogSource
    @Inject
    constructor(
        private val api: OpenFoodFactsApi,
        private val json: Json,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : CatalogRemoteSource {
        private val mapper = TaxonomyCatalogMapper()
        private val serializer = MapSerializer(String.serializer(), TaxonomyEntryDto.serializer())

        override val formatVersion: Int = TaxonomyCatalogMapper.FORMAT_VERSION

        @OptIn(ExperimentalSerializationApi::class)
        override suspend fun fetch(currentVersion: String?): RemoteCatalogResult =
            withContext(ioDispatcher) {
                try {
                    val response = api.downloadCategories(currentVersion)
                    if (response.code() == HttpURLConnection.HTTP_NOT_MODIFIED) {
                        return@withContext RemoteCatalogResult.NotModified
                    }
                    val body = response.body()
                    if (!response.isSuccessful || body == null) {
                        response.errorBody()?.close()
                        throw CatalogDownloadException("HTTP ${response.code()}")
                    }
                    val entries = body.use { json.decodeFromStream(serializer, it.byteStream()) }
                    val version = response.headers()["ETag"] ?: response.headers()["Last-Modified"] ?: UNKNOWN_VERSION
                    RemoteCatalogResult.Updated(version, mapper.map(entries))
                } catch (exception: IOException) {
                    throw CatalogDownloadException("Network error", exception)
                } catch (exception: SerializationException) {
                    throw CatalogDownloadException("Unexpected catalog format", exception)
                }
            }

        private companion object {
            const val UNKNOWN_VERSION = "unversioned"
        }
    }
