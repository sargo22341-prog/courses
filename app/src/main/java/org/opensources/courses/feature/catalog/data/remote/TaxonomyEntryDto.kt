package org.opensources.courses.feature.catalog.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Only the fields useful for autocomplete are decoded; everything else is skipped. */
@Serializable
data class TaxonomyEntryDto(
    val name: Map<String, String> = emptyMap(),
    val parents: List<String> = emptyList(),
    @SerialName("protected_name_type") val protectedNameType: JsonElement? = null,
    val origins: JsonElement? = null,
)
