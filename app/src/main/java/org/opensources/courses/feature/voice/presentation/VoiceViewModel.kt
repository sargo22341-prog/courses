package org.opensources.courses.feature.voice.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.opensources.courses.feature.language.domain.AppLanguageRepository
import org.opensources.courses.core.common.DefaultDispatcher
import org.opensources.courses.feature.shopping.domain.AddItemUseCase
import org.opensources.courses.feature.shopping.domain.QuantityFormatter
import org.opensources.courses.feature.voice.domain.VoiceEntryParser
import org.opensources.courses.feature.voice.domain.VoiceRecognition
import org.opensources.courses.feature.voice.domain.VoiceRecognizer
import javax.inject.Inject

data class VoiceDraft(val id: Int, val name: String, val quantity: String, val unit: String) {
    val valid: Boolean get() = name.isNotBlank() && !name.contains("[unk]") && QuantityFormatter.parse(quantity)?.let { it <= 10_000 } == true
}

data class VoiceUiState(
    val recognition: VoiceRecognition = VoiceRecognition.Idle,
    val transcript: String = "",
    val draft: List<VoiceDraft>? = null,
    val importing: Boolean = false,
    val importFailed: Boolean = false,
)

@HiltViewModel
class VoiceViewModel @Inject constructor(
    private val recognizer: VoiceRecognizer,
    private val languages: AppLanguageRepository,
    private val addItem: AddItemUseCase,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val mutableState = MutableStateFlow(VoiceUiState())
    val uiState = mutableState.asStateFlow()
    private var targetListId: String? = null

    init {
        viewModelScope.launch {
            recognizer.state.collect { recognition ->
                if (recognition is VoiceRecognition.Transcript) {
                    val entries = withContext(defaultDispatcher) { VoiceEntryParser.parse(recognition.text, languages.language.value) }
                    mutableState.value = VoiceUiState(
                        recognition = recognition,
                        transcript = recognition.text,
                        draft = entries.mapIndexed { index, entry ->
                            VoiceDraft(index, entry.name, (entry.quantity ?: 1.0).toString().removeSuffix(".0"), entry.unit.orEmpty())
                        },
                    )
                } else {
                    mutableState.value = mutableState.value.copy(recognition = recognition)
                }
            }
        }
    }

    fun start(listId: String) {
        if (uiState.value.draft != null || uiState.value.importing) return
        targetListId = listId
        recognizer.start(languages.language.value.tag, viewModelScope)
    }

    fun stop() = recognizer.stop()
    fun cancelRecording() = recognizer.cancel()

    fun edit(row: VoiceDraft) {
        if (uiState.value.importing) return
        mutableState.value = uiState.value.copy(draft = uiState.value.draft?.map { if (it.id == row.id) row else it })
    }

    fun delete(id: Int) {
        if (uiState.value.importing) return
        mutableState.value = uiState.value.copy(draft = uiState.value.draft?.filterNot { it.id == id })
    }

    fun dismiss() {
        if (uiState.value.importing) return
        recognizer.cancel()
        mutableState.value = VoiceUiState()
        targetListId = null
    }

    fun confirm() {
        val current = uiState.value
        val draft = current.draft ?: return
        val listId = targetListId ?: return
        if (current.importing || draft.isEmpty() || draft.any { !it.valid }) return
        mutableState.value = current.copy(importing = true, importFailed = false)
        viewModelScope.launch {
            try {
                val completed = withTimeoutOrNull(30_000) {
                    for (row in draft) {
                        checkNotNull(addItem(listId, row.name, quantity = QuantityFormatter.parse(row.quantity), unit = row.unit.trim().ifBlank { null }))
                        // Successfully imported rows cannot be imported twice after a later failure.
                        mutableState.value = uiState.value.copy(draft = uiState.value.draft?.filterNot { it.id == row.id })
                    }
                    true
                }
                mutableState.value = if (completed == true) VoiceUiState() else uiState.value.copy(importFailed = true)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                mutableState.value = uiState.value.copy(importFailed = true)
            } finally {
                mutableState.value = uiState.value.copy(importing = false)
            }
        }
    }

    override fun onCleared() = recognizer.cancel()
}
