package org.opensources.courses.feature.voice.data

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.courses.feature.catalog.domain.TextNormalizer
import org.opensources.courses.feature.language.domain.AppLanguage
import org.opensources.courses.feature.voice.domain.VoiceEntryParser
import org.opensources.courses.feature.voice.domain.VoiceVocabularyUseCase
import org.opensources.courses.feature.catalog.data.seed.AssetSeedCatalogSource
import org.opensources.courses.feature.catalog.data.taxonomy.AssetTaxonomyCatalogSource
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class VoskEngineTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun syntheticBinBagsKeepSpokenCount() = runBlocking {
        val path = InstrumentationRegistry.getArguments().getString("binBagsFixture")
        assumeTrue("Provide a synthetic PCM WAV fixture with -e binBagsFixture", path != null)
        val pcm = readPcm(File(checkNotNull(path)))
        val transcript = transcribe(pcm)
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "Synthetic bin bags transcription: $transcript\n") })
        val row = VoiceEntryParser.parse(transcript, AppLanguage.FRENCH).single()
        assertTrue(row.name.contains("poubelle"))
        val expected = InstrumentationRegistry.getArguments().getString("binBagsQuantity")?.toDouble() ?: 2.0
        assertEquals(expected, row.quantity ?: 1.0, 0.0)
    }

    @Test fun allSixBundledModelsLoadAndRecognizeSilence() = runBlocking {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        val store = VoiceModelStore(instrumentation.targetContext)
        for (language in AppLanguage.entries) {
            Model(store.directory(language).absolutePath).use { model ->
                Recognizer(model, 16_000f).use { recognizer ->
                    recognizer.acceptWaveForm(ShortArray(16_000), 16_000)
                    assertEquals("", text(recognizer.finalResult))
                }
            }
        }
    }

    /** Optional locally synthesized fixture supplied by ADB; no voice or model is downloaded. */
    @Test fun syntheticFrenchSpeechProducesQuantifiedReview() = runBlocking {
        val path = InstrumentationRegistry.getArguments().getString("voiceFixture")
        assumeTrue("Provide a synthetic PCM WAV fixture with -e voiceFixture", path != null)
        val pcm = readPcm(File(checkNotNull(path)))
        val transcript = transcribe(pcm)
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "Synthetic French transcription: $transcript\n") })
        val rows = VoiceEntryParser.parse(transcript, AppLanguage.FRENCH)
        assertTrue(rows.any { TextNormalizer.normalize(it.name).removeSuffix("s") == "cafe" })
        assertTrue(rows.any { TextNormalizer.normalize(it.name).removeSuffix("s") == "yaourt" && it.quantity == 3.0 })
        assertTrue(rows.any { TextNormalizer.normalize(it.name).removeSuffix("s") == "pate" && it.quantity == 500.0 && it.unit == "g" })
    }

    private suspend fun transcribe(pcm: ByteArray): String {
        val assetJson = Json { ignoreUnknownKeys = true }
        val sources = listOf(
            AssetSeedCatalogSource(instrumentation.targetContext, assetJson, Dispatchers.IO),
            AssetTaxonomyCatalogSource(instrumentation.targetContext, assetJson, Dispatchers.IO),
        )
        val grammar = Json.encodeToString(VoiceVocabularyUseCase(sources)(AppLanguage.FRENCH))
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        val store = VoiceModelStore(instrumentation.targetContext)
        val recording = VoiceAudioCapture()
        require(pcm.size <= recording.samples.size * 2 && pcm.size % 2 == 0)
        val samples = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        samples.get(recording.samples, 0, samples.remaining())
        recording.count.set(pcm.size / 2)
        recording.finished.set(true)
        return Model(store.directory(AppLanguage.FRENCH).absolutePath).use { model ->
            VoskTranscriptDecoder.recognize(recording, model, grammar, AppLanguage.FRENCH)
        }
    }

    private fun text(result: String): String = Json.parseToJsonElement(result).jsonObject.getValue("text").jsonPrimitive.content

    private fun readPcm(file: File): ByteArray {
        require(file.length() in 44..8_000_000)
        val bytes = file.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF")
        require(String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE")
        buffer.position(12)
        var formatChecked = false
        repeat(32) {
            val type = ByteArray(4).also(buffer::get).toString(Charsets.US_ASCII)
            val length = buffer.int
            val start = buffer.position()
            require(length >= 0 && length <= buffer.remaining())
            if (type == "fmt ") {
                require(buffer.short.toInt() == 1 && buffer.short.toInt() == 1 && buffer.int == 16_000)
                buffer.position(start + 14)
                require(buffer.short.toInt() == 16)
                formatChecked = true
            }
            if (type == "data") {
                require(formatChecked)
                return bytes.copyOfRange(start, start + length)
            }
            buffer.position(start + length + length % 2)
        }
        error("WAV data chunk not found")
    }
}
