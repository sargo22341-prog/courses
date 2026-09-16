package org.opensources.courses.feature.catalog.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.courses.testing.FakeCatalogRepository
import org.opensources.courses.testing.fixedClock
import org.opensources.courses.testing.product
import java.util.concurrent.Executors

class SearchSuggestionsUseCaseTest {
    private val catalog =
        FakeCatalogRepository(
            listOf(
                product("Lait", baseScore = 8),
                product("Lait demi-écrémé", baseScore = 6),
                product("Lait entier", baseScore = 6),
                product("Lait d'amande", baseScore = 6),
                product("Tomates", baseScore = 8),
                product("Tomates cerises", baseScore = 6),
                product("Tomates concassées", baseScore = 6),
                product("Pain", baseScore = 8),
            ),
        )
    private val search = SearchSuggestionsUseCase(catalog, fixedClock(), Dispatchers.Unconfined)

    @Test
    fun `tom suggests tomatoes first`() =
        runTest {
            assertEquals(listOf("Tomates", "Tomates cerises", "Tomates concassées"), search("tom").map { it.name })
        }

    @Test
    fun `lai suggests the milks`() =
        runTest {
            assertEquals(listOf("Lait", "Lait entier", "Lait d'amande", "Lait demi-écrémé"), search("lai").map { it.name })
        }

    @Test
    fun `falls back to typo tolerant matching`() =
        runTest {
            assertEquals("Tomates", search("tomatos").first().name)
        }

    @Test
    fun `blank query suggests nothing`() =
        runTest {
            assertTrue(search("   ").isEmpty())
        }

    @Test
    fun `accents and case are ignored`() =
        runTest {
            assertEquals("Lait demi-écrémé", search("DEMI ECREME").first().name)
        }

    @Test
    fun `the exact suggestion carries the normalized name it was matched on`() =
        runTest {
            assertEquals("lait d amande", search("lait d'amande").first().normalizedName)
        }

    @Test
    fun `candidates are read and ranked on the dispatcher given for CPU work, not on the caller's`() =
        runTest {
            var rankingThread: Thread? = null
            val executor = Executors.newSingleThreadExecutor { Thread(it).also { thread -> rankingThread = thread } }
            try {
                var searchedOn: Thread? = null
                val fake = FakeCatalogRepository(listOf(product("Lait")))
                val recording =
                    object : CatalogRepository by fake {
                        override suspend fun findCandidates(
                            normalizedQuery: String,
                            limit: Int,
                        ) = fake.findCandidates(normalizedQuery, limit).also { searchedOn = Thread.currentThread() }
                    }

                val found = SearchSuggestionsUseCase(recording, fixedClock(), executor.asCoroutineDispatcher())("lait")

                assertEquals(listOf("Lait"), found.map { it.name })
                assertEquals(rankingThread, searchedOn)
            } finally {
                executor.shutdown()
            }
        }
}
