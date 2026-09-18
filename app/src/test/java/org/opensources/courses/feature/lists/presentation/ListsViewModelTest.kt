package org.opensources.courses.feature.lists.presentation

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.opensources.courses.testing.FakeShoppingListRepository
import org.opensources.courses.testing.MainDispatcherRule

class ListsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repository = FakeShoppingListRepository()
    // Created once the rule installed the main dispatcher.
    private val viewModel by lazy { ListsViewModel(repository) }

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
}
