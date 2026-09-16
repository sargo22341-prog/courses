package org.opensources.courses.feature.shopping.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.courses.feature.catalog.domain.CatalogRepository
import org.opensources.courses.feature.catalog.domain.GroceryCategory
import org.opensources.courses.feature.language.domain.AppLanguage
import org.opensources.courses.testing.FakeAppLanguageRepository
import org.opensources.courses.testing.FakeCatalogRepository
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
class GroupItemsByCategoryUseCaseTest {
    private val catalog =
        FakeCatalogRepository().apply {
            categories["lait"] = GroceryCategory.DAIRY_EGGS
            categories["yaourts"] = GroceryCategory.DAIRY_EGGS
            categories["tomates"] = GroceryCategory.FRUITS_VEGETABLES
            categories["pains"] = GroceryCategory.BAKERY
        }
    private val languages = FakeAppLanguageRepository(AppLanguage.FRENCH)
    private val group = GroupItemsByCategoryUseCase(catalog, languages, Dispatchers.Unconfined)

    private fun item(
        name: String,
        catalogProductId: String? = null,
        checked: Boolean = false,
    ) = ShoppingItem("id-$name", "list", name, 1.0, null, checked, catalogProductId)

    private suspend fun sections(vararg items: ShoppingItem): List<ItemSection> = group(flowOf(items.toList())).first().sections

    @Test
    fun `sections follow the store order with unknown names last`() =
        runTest {
            val sections = sections(item("Sauce maison"), item("Lait"), item("Pain"), item("Tomates"))

            assertEquals(
                listOf(GroceryCategory.FRUITS_VEGETABLES, GroceryCategory.BAKERY, GroceryCategory.DAIRY_EGGS, GroceryCategory.OTHER),
                sections.map { it.category },
            )
            assertEquals(listOf("Sauce maison"), sections.last().items.map { it.name })
        }

    @Test
    fun `names written differently than in the catalog still find their section`() =
        runTest {
            // As they may come from Home Assistant: other case, singular, extra spaces.
            val sections = sections(item("TOMATE"), item(" yaourt "))

            assertEquals(listOf(GroceryCategory.FRUITS_VEGETABLES, GroceryCategory.DAIRY_EGGS), sections.map { it.category })
        }

    @Test
    fun `items keep their order inside a section`() =
        runTest {
            val sections = sections(item("Yaourts"), item("Pain"), item("Lait"))

            assertEquals(listOf("Yaourts", "Lait"), sections.first { it.category == GroceryCategory.DAIRY_EGGS }.items.map { it.name })
        }

    @Test
    fun `an item written in the previous language keeps the section of its linked product`() =
        runTest {
            languages.setLanguage(AppLanguage.ENGLISH)
            catalog.categories.clear()
            catalog.categoriesById["seed:lait"] = GroceryCategory.DAIRY_EGGS

            val sections = sections(item("Lait", catalogProductId = "seed:lait"), item("Lait", catalogProductId = null).copy(id = "unlinked"))

            assertEquals(listOf(GroceryCategory.DAIRY_EGGS, GroceryCategory.OTHER), sections.map { it.category })
        }

    @Test
    fun `the name wins over the link`() =
        runTest {
            catalog.categoriesById["seed:lait"] = GroceryCategory.DAIRY_EGGS

            val sections = sections(item("Pain", catalogProductId = "seed:lait"))

            assertEquals(listOf(GroceryCategory.BAKERY), sections.map { it.category })
        }

    @Test
    fun `plural forms follow the app language`() =
        runTest {
            languages.setLanguage(AppLanguage.ITALIAN)
            catalog.categories["pomodori"] = GroceryCategory.FRUITS_VEGETABLES

            val sections = sections(item("Pomodoro"))

            assertEquals(listOf(GroceryCategory.FRUITS_VEGETABLES), sections.map { it.category })
        }

    @Test
    fun `checking an item sorts the list again without querying the catalog`() =
        runTest {
            val items = MutableStateFlow(listOf(item("Lait"), item("Pain")))
            val emitted = mutableListOf<CategorizedItems>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { group(items).toList(emitted) }
            val queriesAtStart = catalog.categoryQueries

            items.value = listOf(item("Lait", checked = true), item("Pain"))

            assertEquals(queriesAtStart, catalog.categoryQueries)
            assertTrue(emitted.last().items.first().isChecked)
            assertEquals(listOf("Pain"), emitted.last().sections.withoutChecked().flatMap { section -> section.items.map { it.name } })
        }

    @Test
    fun `a renamed item is sorted under its new name`() =
        runTest {
            val items = MutableStateFlow(listOf(item("Lait")))
            val emitted = mutableListOf<CategorizedItems>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { group(items).toList(emitted) }

            items.value = listOf(item("Pain").copy(id = "id-Lait"))

            assertEquals(listOf(GroceryCategory.BAKERY), emitted.last().sections.map { it.category })
        }

    @Test
    fun `items are sorted on the dispatcher given for CPU work`() =
        runTest {
            var sortingThread: Thread? = null
            val executor = Executors.newSingleThreadExecutor { Thread(it).also { thread -> sortingThread = thread } }
            try {
                var queriedOn: Thread? = null
                val recording =
                    object : CatalogRepository by catalog {
                        override fun observeCategories(normalizedNames: Set<String>): Flow<Map<String, GroceryCategory>> {
                            queriedOn = Thread.currentThread()
                            return catalog.observeCategories(normalizedNames)
                        }
                    }
                val sorting = GroupItemsByCategoryUseCase(recording, languages, executor.asCoroutineDispatcher())

                val sections = sorting(flowOf(listOf(item("Lait")))).first().sections

                assertEquals(listOf(GroceryCategory.DAIRY_EGGS), sections.map { it.category })
                assertEquals(sortingThread, queriedOn)
            } finally {
                executor.shutdown()
            }
        }

    @Test
    fun `an added item is shown only once its section is known`() =
        runTest {
            val items = MutableStateFlow(listOf(item("Lait")))
            val emitted = mutableListOf<CategorizedItems>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { group(items).toList(emitted) }

            items.value = listOf(item("Lait"), item("Tomates"))

            // Never "Tomates" in "Autres" first, then moved to its section.
            emitted.forEach { categorized ->
                val other = categorized.sections.firstOrNull { it.category == GroceryCategory.OTHER }
                assertTrue(other == null)
            }
            assertEquals(
                listOf(GroceryCategory.FRUITS_VEGETABLES, GroceryCategory.DAIRY_EGGS),
                emitted.last().sections.map { it.category },
            )
        }

    @Test
    fun `checked items leave their section, and an empty section disappears`() {
        val sections =
            listOf(
                ItemSection(GroceryCategory.BAKERY, listOf(item("Pain", checked = true))),
                ItemSection(GroceryCategory.DAIRY_EGGS, listOf(item("Lait"), item("Yaourts", checked = true))),
            )

        val toBuy = sections.withoutChecked()

        assertEquals(listOf(GroceryCategory.DAIRY_EGGS), toBuy.map { it.category })
        assertEquals(listOf("Lait"), toBuy.single().items.map { it.name })
    }
}
