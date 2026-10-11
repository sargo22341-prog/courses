package org.opensources.courses.feature.voice.data

import android.Manifest
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.opensources.courses.core.common.IoDispatcher
import org.opensources.courses.core.permission.isPermissionGranted
import org.opensources.courses.feature.language.domain.AppLanguage
import org.opensources.courses.feature.voice.domain.VoiceRecognition
import org.opensources.courses.feature.voice.domain.VoiceRecognizer
import org.opensources.courses.feature.voice.domain.VoiceVocabularyUseCase
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import javax.inject.Inject

/** Audio and decoding stay on the device and all work is owned by the calling ViewModel. */
class VoskVoiceRecognizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val models: VoiceModelStore,
    private val vocabulary: VoiceVocabularyUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : VoiceRecognizer {
    private val mutableState = MutableStateFlow<VoiceRecognition>(VoiceRecognition.Idle)
    override val state = mutableState.asStateFlow()
    @Volatile private var capture: VoiceAudioCapture? = null
    private var job: Job? = null

    override fun start(languageTag: String, scope: CoroutineScope) {
        cancel()
        val language = AppLanguage.fromTag(languageTag)
        if (language == null || !context.isPermissionGranted(Manifest.permission.RECORD_AUDIO)) {
            mutableState.value = VoiceRecognition.Failure()
            return
        }
        val recording = VoiceAudioCapture()
        capture = recording
        mutableState.value = VoiceRecognition.Listening
        job = scope.launch(ioDispatcher) { recognize(recording, language) }
    }

    private suspend fun recognize(recording: VoiceAudioCapture, language: AppLanguage) {
        try {
            val text = withTimeoutOrNull(90_000) {
                coroutineScope {
                    launch {
                        // Permission can be revoked between the boundary check and the audio call;
                        // SecurityException then becomes a visible failure below.
                        @Suppress("MissingPermission")
                        recording.record {
                            if (capture === recording) mutableState.value = VoiceRecognition.Processing
                        }
                    }
                    // Only public, shipped words enter the grammar, never dictated text or history.
                    LibVosk.setLogLevel(LogLevel.WARNINGS)
                    val grammar = Json.encodeToString(vocabulary(language))
                    Model(models.directory(language).absolutePath).use { model ->
                        currentCoroutineContext().ensureActive()
                        VoskTranscriptDecoder.recognize(recording, model, grammar, language)
                    }
                }
            }
            if (capture === recording) {
                capture = null
                mutableState.value = if (text.isNullOrBlank()) VoiceRecognition.Failure() else VoiceRecognition.Transcript(text)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            if (capture === recording) { capture = null; mutableState.value = VoiceRecognition.Failure() }
        } catch (_: UnsatisfiedLinkError) {
            // A damaged native engine must fail visibly, without taking the local list down.
            if (capture === recording) { capture = null; mutableState.value = VoiceRecognition.Failure(unavailable = true) }
        }
    }

    override fun stop() {
        capture?.capturing?.set(false)
        if (capture != null) mutableState.value = VoiceRecognition.Processing
    }

    override fun cancel() {
        capture?.capturing?.set(false)
        capture = null
        job?.cancel()
        job = null
        mutableState.value = VoiceRecognition.Idle
    }
}
