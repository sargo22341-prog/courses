package org.opensources.courses.feature.catalog.data.remote

import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.modules.SerializersModule
import org.opensources.courses.feature.language.domain.AppLanguage

/**
 * Only the fields useful for autocomplete are decoded; everything else is skipped.
 *
 * @property isProtectedName a protected designation (AOP/IGP…); its details are not kept.
 * @property hasOrigins tied to an origin (`Miels du Jura`); its details are not kept.
 */
@Serializable
data class TaxonomyEntryDto(
    @Contextual val name: TaxonomyName = TaxonomyName.NONE,
    val parents: List<String> = emptyList(),
    @SerialName("protected_name_type") @Serializable(with = PresenceSerializer::class) val isProtectedName: Boolean = false,
    @SerialName("origins") @Serializable(with = PresenceSerializer::class) val hasOrigins: Boolean = false,
)

/** Name of an entry in the language it was read in; [text] is null when the entry has none in it. */
data class TaxonomyName(
    val text: String?,
) {
    companion object {
        val NONE = TaxonomyName(null)
    }
}

/**
 * Json reading the taxonomy in [language]. The file names its ~15 000 entries in about 180 languages:
 * the other names are dropped as soon as they are read, instead of being kept for the whole import.
 */
fun taxonomyJson(
    base: Json,
    language: AppLanguage,
): Json = Json(base) { serializersModule = SerializersModule { contextual(TaxonomyName::class, TaxonomyNameSerializer(language.tag)) } }

private class TaxonomyNameSerializer(
    private val languageTag: String,
) : KSerializer<TaxonomyName> {
    override val descriptor: SerialDescriptor =
        SerialDescriptor(TaxonomyName::class.java.name, MapSerializer(String.serializer(), String.serializer()).descriptor)

    // Same reading order as the library's map serializer: key index, key, value index, value.
    override fun deserialize(decoder: Decoder): TaxonomyName =
        decoder.decodeStructure(descriptor) {
            var text: String? = null
            while (true) {
                val keyIndex = decodeElementIndex(descriptor)
                if (keyIndex == CompositeDecoder.DECODE_DONE) break
                val tag = decodeStringElement(descriptor, keyIndex)
                val name = decodeStringElement(descriptor, decodeElementIndex(descriptor))
                if (tag == languageTag) text = name
            }
            TaxonomyName(text)
        }

    override fun serialize(
        encoder: Encoder,
        value: TaxonomyName,
    ) = throw SerializationException("The taxonomy is only read")
}

/** Whether the field holds a value; the value itself is read and dropped. */
private object PresenceSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor = SerialDescriptor(PresenceSerializer::class.java.name, JsonElement.serializer().descriptor)

    override fun deserialize(decoder: Decoder): Boolean {
        val json = decoder as? JsonDecoder ?: throw SerializationException("The taxonomy is read as JSON")
        return json.decodeJsonElement() != JsonNull
    }

    override fun serialize(
        encoder: Encoder,
        value: Boolean,
    ) = throw SerializationException("The taxonomy is only read")
}
