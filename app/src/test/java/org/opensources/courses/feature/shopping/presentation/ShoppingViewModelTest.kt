package org.opensources.courses.feature.shopping.presentation

import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.opensources.courses.feature.catalog.domain.GroceryCategory
import org.opensources.courses.feature.catalog.domain.SearchSuggestionsUseCase
import org.opensources.courses.feature.settings.domain.AppPreferences
import org.opensources.courses.feature.shopping.domain.AddItemUseCase
import org.opensources.courses.feature.shopping.domain.GroupItemsByCategoryUseCase
import org.opensources.courses.feature.shopping.domain.ProductHistoryUseCase
import org.opensources.courses.feature.shopping.domain.ShoppingItem
import org.opensources.courses.testing.FakeAppLanguageRepository
import org.opensources.courses.testing.FakeAppPreferencesRepository
import org.opensources.courses.testing.FakeCatalogRepository
import org.opensources.courses.testing.FakeRemoteSyncEngine
import org.opensources.courses.testing.FakeShoppingItemRepository
import org.opensources.courses.testing.FakeShoppingListRepository
import org.opensources.courses.testing.MainDispatcherRule
import org.opensources.courses.testing.fixedClock
import org.opensources.courses.testing.product
import org.opensources.courses.testing.syncCoordinator

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val lists = FakeShoppingListRepository()
    private val items = FakeShoppingItemRepository()
    private val catalog = FakeCatalogRepository(listOf(product("Lait"), product("Lait d'amande"))).apply { categories["lait"] = GroceryCategory.DAIRY_EGGS }
    private val languages = FakeAppLanguageRepository()
    private val preferences = FakeAppPreferencesRepository(AppPreferences.Default.copy(groupByCategory = true))
    private val engine = FakeRemoteSyncEngine()
    private val store = ViewModelStore()

    private fun TestScope.viewModel(): ShoppingViewModel {
        val factory =
            viewModelFactory {
                initializer {
                    ShoppingViewModel(
                        savedStateHandle = SavedStateHandle(),
                        lists = lists,
                        items = items,
                        addItem = AddItemUseCase(items, catalog),
                        searchSuggestions = SearchSuggestionsUseCase(catalog, fixedClock(), main.dispatcher),
                        groupItemsByCategory = GroupItemsByCategoryUseCase(catalog, languages, main.dispatcher),
                        productHistory = ProductHistoryUseCase(catalog),
                        preferences = preferences,
                        languages = languages,
                        syncCoordinator = syncCoordinator(engine, backgroundScope),
                        applicationScope = backgroundScope,
                    )
                }
            }
        return ViewModelProvider.create(store, factory)[ShoppingViewModel::class].also { viewModel ->
            backgroundScope.launch(main.dispatcher) { viewModel.uiState.collect {} }
        }
    }

    private suspend fun TestScope.itemNamed(
        viewModel: ShoppingViewModel,
        name: String,
    ): ShoppingItem {
        viewModel.onQueryChange(name)
        viewModel.onAddCustomItem()
        runCurrent()
        return items.getAllItems().first { it.name == name }
    }

    @Test
    fun `keyboard done adds the suggestion matching the typed text`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onQueryChange("lait ")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(SEARCH_ELAPSED_MILLIS)
            viewModel.onSubmitQuery()
            runCurrent()

            val added = items.getAllItems().single()
            assertEquals("Lait", added.name)
            assertEquals("id:Lait", added.catalogProductId)
            assertEquals("", viewModel.query)
        }

    @Test
    fun `a quantity typed before the name is searched without it and added with it`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onQueryChange("2 laits")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(SEARCH_ELAPSED_MILLIS)
            runCurrent()
            val shown = viewModel.uiState.value
            assertEquals("laits", shown.searchedEntry.name)
            assertEquals("Lait", shown.exactSuggestion?.name)
            assertFalse(shown.offersCustomItem)

            viewModel.onSubmitQuery()
            runCurrent()

            val added = items.getAllItems().single()
            assertEquals("Lait", added.name)
            assertEquals(2.0, added.quantity, 0.0)
        }

    @Test
    fun `done typed faster than the search still finds the catalog product`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onQueryChange("1 l de lait")
            viewModel.onSubmitQuery()
            runCurrent()

            val added = items.getAllItems().single()
            assertEquals("id:Lait", added.catalogProductId)
            assertEquals(1.0, added.quantity, 0.0)
            assertEquals("L", added.unit)
        }

    @Test
    fun `a chosen suggestion takes the quantity typed`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onQueryChange("500 g de lait")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(SEARCH_ELAPSED_MILLIS)
            runCurrent()

            viewModel.onSuggestionSelected(viewModel.uiState.value.suggestions.first { it.name == "Lait d'amande" })
            runCurrent()

            val added = items.getAllItems().single()
            assertEquals("Lait d'amande", added.name)
            assertEquals(500.0, added.quantity, 0.0)
            assertEquals("g", added.unit)
        }

    @Test
    fun `plus and minus change the quantity and each quick tap counts`() =
        runTest {
            val viewModel = viewModel()
            val bread = itemNamed(viewModel, "Pain")

            viewModel.onChangeQuantity(bread, increase = true)
            viewModel.onChangeQuantity(bread, increase = true)
            runCurrent()
            assertEquals(3.0, items.getAllItems().single().quantity, 0.0)

            repeat(3) { viewModel.onChangeQuantity(bread, increase = false) }
            runCurrent()
            assertEquals(1.0, items.getAllItems().single().quantity, 0.0)
        }

    @Test
    fun `an item just added is highlighted until the list showed it`() =
        runTest {
            val viewModel = viewModel()
            val bread = itemNamed(viewModel, "Pain")

            assertEquals(bread.id, viewModel.uiState.value.highlightedItemId)

            viewModel.onHighlightShown()
            runCurrent()
            assertNull(viewModel.uiState.value.highlightedItemId)
        }

    @Test
    fun `a pull to refresh already running is not started twice`() =
        runTest {
            val viewModel = viewModel()
            engine.pause = CompletableDeferred()

            viewModel.onRefresh()
            viewModel.onRefresh()
            runCurrent()
            assertTrue(viewModel.uiState.value.isRefreshing)

            engine.pause?.complete(Unit)
            runCurrent()

            assertEquals(1, engine.synchronizations)
            assertFalse(viewModel.uiState.value.isRefreshing)
        }

    @Test
    fun `a deleted item is hidden at once and deleted when the undo offer ends`() =
        runTest {
            val viewModel = viewModel()
            val milk = itemNamed(viewModel, "Lait")

            viewModel.onDeleteItem(milk)
            runCurrent()
            val hidden = viewModel.uiState.value
            assertTrue(hidden.toBuy.isEmpty())
            assertTrue(hidden.toBuySections.orEmpty().isEmpty())
            assertEquals(milk, hidden.pendingDeletion)
            assertEquals(listOf(milk.id), items.getAllItems().map { it.id })

            viewModel.onDeletionConfirmed(milk)
            runCurrent()

            assertTrue(items.getAllItems().isEmpty())
            assertNull(viewModel.uiState.value.pendingDeletion)
        }

    @Test
    fun `undoing brings the item back and deletes nothing`() =
        runTest {
            val viewModel = viewModel()
            val milk = itemNamed(viewModel, "Lait")
            viewModel.onDeleteItem(milk)

            viewModel.onUndoDeletion()
            // The offer that just ended must not delete it afterwards.
            viewModel.onDeletionConfirmed(milk)
            runCurrent()

            assertEquals(listOf("Lait"), viewModel.uiState.value.toBuy.map { it.name })
            assertEquals(1, items.getAllItems().size)
        }

    @Test
    fun `deleting another item confirms the previous deletion`() =
        runTest {
            val viewModel = viewModel()
            val milk = itemNamed(viewModel, "Lait")
            val bread = itemNamed(viewModel, "Pain")

            viewModel.onDeleteItem(milk)
            viewModel.onDeleteItem(bread)
            runCurrent()

            assertEquals(listOf("Pain"), items.getAllItems().map { it.name })
            assertEquals(bread, viewModel.uiState.value.pendingDeletion)
        }

    @Test
    fun `leaving the screen for good confirms a pending deletion`() =
        runTest {
            val viewModel = viewModel()
            val milk = itemNamed(viewModel, "Lait")
            viewModel.onDeleteItem(milk)

            store.clear()
            runCurrent()

            assertTrue(items.getAllItems().isEmpty())
        }

    @Test
    fun `the history offers past products that are not waiting in the list`() =
        runTest {
            val viewModel = viewModel()
            val milk = itemNamed(viewModel, "Lait")
            val bread = itemNamed(viewModel, "Pain")
            assertTrue(viewModel.uiState.value.history.isEmpty())

            viewModel.onToggleItem(bread)
            viewModel.onDeleteItem(milk)
            runCurrent()

            // Bought or being deleted: both can be added again from the history.
            assertEquals(setOf("Lait", "Pain"), viewModel.uiState.value.history.map { it.name }.toSet())

            viewModel.onSuggestionSelected(viewModel.uiState.value.history.first { it.name == "Pain" })
            runCurrent()

            assertEquals(listOf("Lait"), viewModel.uiState.value.history.map { it.name })
            assertEquals(listOf("Pain"), viewModel.uiState.value.toBuy.map { it.name })
        }

    @Test
    fun `no history is offered while it is turned off`() =
        runTest {
            val viewModel = viewModel()
            val milk = itemNamed(viewModel, "Lait")
            preferences.state.value = preferences.state.value.copy(historyEnabled = false)
            viewModel.onDeleteItem(milk)
            runCurrent()

            assertTrue(viewModel.uiState.value.history.isEmpty())

            preferences.state.value = preferences.state.value.copy(historyEnabled = true)
            runCurrent()

            assertEquals(listOf("Lait"), viewModel.uiState.value.history.map { it.name })
        }

    private companion object {
        const val SEARCH_ELAPSED_MILLIS = 100L
    }
}
