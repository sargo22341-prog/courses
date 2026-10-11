package org.opensources.courses.feature.voice.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.opensources.courses.feature.shopping.domain.AddItemUseCase
import org.opensources.courses.feature.shopping.domain.NewShoppingItem
import org.opensources.courses.feature.shopping.domain.ShoppingItem
import org.opensources.courses.feature.shopping.domain.ShoppingItemRepository
import org.opensources.courses.feature.voice.domain.VoiceRecognition
import org.opensources.courses.feature.voice.domain.VoiceRecognizer
import org.opensources.courses.testing.FakeAppLanguageRepository
import org.opensources.courses.testing.FakeCatalogRepository
import org.opensources.courses.testing.FakeShoppingItemRepository
import org.opensources.courses.testing.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val recognizer = FakeRecognizer()
    private val items = FakeShoppingItemRepository()
    private lateinit var viewModel: VoiceViewModel

    @Before fun setUp() {
        viewModel = VoiceViewModel(recognizer, FakeAppLanguageRepository(), AddItemUseCase(items, FakeCatalogRepository()), main.dispatcher)
    }

    @Test fun doesNotWriteUntilConfirmedAndUsesEditedRows() = runTest {
        viewModel.start("list1")
        recognizer.state.value = VoiceRecognition.Transcript("500 g de pâtes et du café")
        assertTrue(items.getItems("list1").isEmpty())
        val rows = checkNotNull(viewModel.uiState.value.draft)
        viewModel.edit(rows.first().copy(quantity = "750", name = "pâtes complètes"))
        viewModel.delete(rows.last().id)
        viewModel.confirm()
        val added = items.getItems("list1").single()
        assertEquals("pâtes complètes", added.name)
        assertEquals(750.0, added.quantity, 0.0)
        assertEquals("g", added.unit)
        assertNull(viewModel.uiState.value.draft)
        viewModel.confirm()
        assertEquals(750.0, items.getItems("list1").single().quantity, 0.0)
    }

    @Test fun dismissalAndInvalidQuantitiesDoNotWrite() = runTest {
        viewModel.start("list1")
        recognizer.state.value = VoiceRecognition.Transcript("du café")
        viewModel.edit(checkNotNull(viewModel.uiState.value.draft).single().copy(quantity = "0"))
        viewModel.confirm()
        assertTrue(items.getItems("list1").isEmpty())
        viewModel.dismiss()
        assertNull(viewModel.uiState.value.draft)
        assertTrue(items.getItems("list1").isEmpty())
    }

    @Test fun twoBinBagsKeepTheirQuantityFromTranscriptToShoppingList() = runTest {
        viewModel.start("list1")
        recognizer.state.value = VoiceRecognition.Transcript("Deux Sac poubelle.")
        val row = checkNotNull(viewModel.uiState.value.draft).single()
        assertEquals("sac poubelle", row.name)
        assertEquals("2", row.quantity)
        viewModel.confirm()
        assertEquals(2.0, items.getItems("list1").single().quantity, 0.0)
    }

    @Test fun releaseRequestsStopAndUnavailableServiceIsVisible() {
        viewModel.start("list1")
        assertEquals(VoiceRecognition.Listening, viewModel.uiState.value.recognition)
        viewModel.stop()
        assertEquals(VoiceRecognition.Processing, viewModel.uiState.value.recognition)
        recognizer.state.value = VoiceRecognition.Failure(unavailable = true)
        assertEquals(VoiceRecognition.Failure(true), viewModel.uiState.value.recognition)
    }

    @Test fun retryAfterPartialFailureDoesNotAddSuccessfulRowsAgain() = runTest {
        var failCoffee = true
        val repository = object : ShoppingItemRepository by items {
            override suspend fun addItem(item: NewShoppingItem): ShoppingItem {
                if (item.name == "café" && failCoffee) error("Simulated storage failure")
                return items.addItem(item)
            }
        }
        val model = VoiceViewModel(recognizer, FakeAppLanguageRepository(), AddItemUseCase(repository, FakeCatalogRepository()), main.dispatcher)
        model.start("list1")
        recognizer.state.value = VoiceRecognition.Transcript("500 g de pâtes et du café")
        model.confirm()
        assertTrue(model.uiState.value.importFailed)
        assertEquals("café", checkNotNull(model.uiState.value.draft).single().name)
        failCoffee = false
        model.confirm()
        assertEquals(2, items.getItems("list1").size)
        assertEquals(500.0, items.getItems("list1").first().quantity, 0.0)
        assertNull(model.uiState.value.draft)
    }

    @Test fun cancellingRecordingDoesNotEraseAnExistingReview() {
        viewModel.start("list1")
        recognizer.state.value = VoiceRecognition.Transcript("du café")
        viewModel.cancelRecording()
        assertEquals("café", checkNotNull(viewModel.uiState.value.draft).single().name)
    }

    @Test fun unrecognizedWordRequiresCorrectionBeforeImport() = runTest {
        viewModel.start("list1")
        recognizer.state.value = VoiceRecognition.Transcript("du [unk]")
        viewModel.confirm()
        assertTrue(items.getItems("list1").isEmpty())
        val row = checkNotNull(viewModel.uiState.value.draft).single()
        viewModel.edit(row.copy(name = "café"))
        viewModel.confirm()
        assertEquals("café", items.getItems("list1").single().name)
    }

    private class FakeRecognizer : VoiceRecognizer {
        override val state = MutableStateFlow<VoiceRecognition>(VoiceRecognition.Idle)
        override fun start(languageTag: String, scope: CoroutineScope) { state.value = VoiceRecognition.Listening }
        override fun stop() { state.value = VoiceRecognition.Processing }
        override fun cancel() { state.value = VoiceRecognition.Idle }
    }
}
