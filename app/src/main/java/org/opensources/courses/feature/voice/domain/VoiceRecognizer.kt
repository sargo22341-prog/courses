package org.opensources.courses.feature.voice.domain

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope

sealed interface VoiceRecognition {
    data object Idle : VoiceRecognition
    data object Listening : VoiceRecognition
    data object Processing : VoiceRecognition
    data class Transcript(val text: String) : VoiceRecognition
    data class Failure(val unavailable: Boolean = false) : VoiceRecognition
}

interface VoiceRecognizer {
    val state: StateFlow<VoiceRecognition>
    fun start(languageTag: String, scope: CoroutineScope)
    fun stop()
    fun cancel()
}
