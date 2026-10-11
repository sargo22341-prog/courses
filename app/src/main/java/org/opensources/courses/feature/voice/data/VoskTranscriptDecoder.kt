package org.opensources.courses.feature.voice.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.opensources.courses.feature.language.domain.AppLanguage
import org.opensources.courses.feature.voice.domain.VoiceQuantityTranscriptResolver
import org.vosk.Model
import org.vosk.Recognizer

/** Both passes reuse the same bounded in-memory audio and remain within the session deadline. */
internal object VoskTranscriptDecoder {
    suspend fun recognize(recording: VoiceAudioCapture, model: Model, grammar: String, language: AppLanguage): String {
        val guided = Recognizer(model, VoiceAudioCapture.SAMPLE_RATE.toFloat(), grammar).use { decode(recording, it) }
        if (!VoiceQuantityTranscriptResolver.needsQuantityCheck(guided, language)) return guided
        currentCoroutineContext().ensureActive()
        // The vocabulary grammar can prefer "de" to "deux". The model's original language
        // context can recover that count; a product-name match is required before using it.
        val unrestricted = Recognizer(model, VoiceAudioCapture.SAMPLE_RATE.toFloat()).use { decode(recording, it) }
        return VoiceQuantityTranscriptResolver.resolve(guided, unrestricted, language)
    }

    private suspend fun decode(recording: VoiceAudioCapture, decoder: Recognizer): String {
        val buffer = ShortArray(1_600)
        val transcript = StringBuilder()
        var offset = 0
        while (!recording.finished.get() || offset < recording.count.get()) {
            currentCoroutineContext().ensureActive()
            val size = minOf(buffer.size, recording.count.get() - offset)
            if (size == 0) { delay(20); continue }
            recording.samples.copyInto(buffer, endIndex = offset + size, startIndex = offset)
            if (decoder.acceptWaveForm(buffer, size)) append(transcript, decoder.result)
            offset += size
        }
        append(transcript, decoder.finalResult)
        return transcript.toString().trim()
    }

    private fun append(transcript: StringBuilder, result: String) {
        val text = Json.parseToJsonElement(result).jsonObject.getValue("text").jsonPrimitive.content
        if (text.isNotBlank() && transcript.length < 4_000) {
            transcript.append(' ').append(text.take(3_999 - transcript.length))
        }
    }
}
