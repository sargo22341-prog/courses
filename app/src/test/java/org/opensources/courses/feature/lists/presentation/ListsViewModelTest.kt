package org.opensources.courses.feature.lists.presentation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.opensources.courses.core.sync.ImportableLists
import org.opensources.courses.core.sync.RemoteListChoice
import org.opensources.courses.core.sync.SyncFailure
import org.opensources.courses.testing.FakeRemoteListImport
import org.opensources.courses.testing.FakeShoppingListRepository
import org.opensources.courses.testing.MainDispatcherRule

class ListsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repository = FakeShoppingListRepository()
    private val remoteImport = FakeRemoteListImport()

    // Created once the rule installed the main dispatcher.
    private val viewModel by lazy { ListsViewModel(repository, remoteImport) }

    @Test
    fun `a created list is offered to be opened once`() =
        runTest {
            viewModel.create("  BBQ ")

            val created = repository.lists.value.single()
            assertEquals("BBQ", created.name)
            assertEquals(created.id, viewModel.createdListId.value)

            viewModel.createdListOpened()
            assertNull(viewModel.createdListId.value)
        }

    @Test
    fun `a blank name creates nothing`() =
        runTest {
            viewModel.create("   ")

            assertTrue(repository.lists.value.isEmpty())
            assertNull(viewModel.createdListId.value)
        }

    @Test
    fun `a dropped list keeps the order chosen`() =
        runTest {
            val (first, second, third) = listOf("A", "B", "C").map { repository.createList(it) }
            backgroundScope.launch(main.dispatcher) { viewModel.lists.collect {} }

            viewModel.reorder(listOf(third.id, first.id, second.id))

            assertEquals(listOf("C", "A", "B"), repository.lists.value.map { it.name })
        }

    @Test
    fun `moving from the menu goes one place and stops at the ends`() =
        runTest {
            val (first, _, third) = listOf("A", "B", "C").map { repository.createList(it) }
            backgroundScope.launch(main.dispatcher) { viewModel.lists.collect {} }

            viewModel.move(third.id, -1)
            assertEquals(listOf("A", "C", "B"), repository.lists.value.map { it.name })

            viewModel.move(first.id, -1)
            viewModel.move(third.id, 5)
            assertEquals(listOf("A", "C", "B"), repository.lists.value.map { it.name })
        }

    @Test
    fun `deleting the last list is refused with a warning shown once`() =
        runTest {
            val only = repository.createList("Courses")

            viewModel.delete(only.id)
            assertTrue(viewModel.lastListWarning.value)
            assertEquals(1, repository.lists.value.size)

            viewModel.lastListWarningShown()
            assertFalse(viewModel.lastListWarning.value)
        }

    @Test
    fun `the import is offered only when the remote allows it`() =
        runTest {
            backgroundScope.launch(main.dispatcher) { viewModel.canImportRemoteList.collect {} }
            assertTrue(viewModel.canImportRemoteList.value)

            remoteImport.isAvailable.value = false
            assertFalse(viewModel.canImportRemoteList.value)
        }

    @Test
    fun `an imported list is offered to be opened once and the dialog closes`() =
        runTest {
            val mealie = RemoteListChoice("todo.mealie", "Mealie")
            remoteImport.result = ImportableLists.Loaded(listOf(mealie))

            viewModel.openImport()
            assertEquals(ListImportUiState.Choosing(listOf(mealie)), viewModel.importState.value)

            viewModel.importList(mealie)
            assertEquals(listOf(mealie), remoteImport.imported)
            assertEquals("imported:todo.mealie", viewModel.createdListId.value)
            assertEquals(ListImportUiState.Closed, viewModel.importState.value)
        }

    @Test
    fun `a failed reading is shown and can be retried`() =
        runTest {
            remoteImport.result = ImportableLists.Failed(SyncFailure.UNREACHABLE)
            viewModel.openImport()
            assertEquals(ListImportUiState.Failed(SyncFailure.UNREACHABLE), viewModel.importState.value)

            remoteImport.result = ImportableLists.Loaded(emptyList())
            viewModel.openImport()
            assertEquals(ListImportUiState.Choosing(emptyList()), viewModel.importState.value)
        }

    @Test
    fun `a reading answered after the dialog was closed does not open it again`() =
        runTest {
            remoteImport.pause = CompletableDeferred()
            viewModel.openImport()
            assertEquals(ListImportUiState.Loading, viewModel.importState.value)

            viewModel.closeImport()
            remoteImport.pause?.complete(Unit)

            assertEquals(ListImportUiState.Closed, viewModel.importState.value)
            assertTrue(remoteImport.imported.isEmpty())
        }
}
