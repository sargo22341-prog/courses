package org.opensources.courses.feature.catalog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.courses.testing.TestNow
import org.opensources.courses.testing.product

class SuggestionRankerTest {
    private val ranker = SuggestionRanker()
    private val now = TestNow.toEpochMilli()
    private val day = 86_400_000L

    private fun rankNames(
        query: String,
        vararg candidates: ProductCandidate,
        limit: Int = 10,
    ): List<String> = ranker.rank(searchQuery(query), candidates.toList(), now, limit).map { it.product.name }

    private fun searchQuery(text: String) = SearchQuery(TextNormalizer.normalize(text))

    @Test
    fun `match kinds are ordered exact, prefix, word prefix, partial, fuzzy`() {
        assertEquals(MatchKind.EXACT, ranker.matchKind(searchQuery("lait"), "lait"))
        assertEquals(MatchKind.PREFIX, ranker.matchKind(searchQuery("lai"), "lait entier"))
        assertEquals(MatchKind.WORD_PREFIX, ranker.matchKind(searchQuery("tom"), "sauce tomate"))
        assertEquals(MatchKind.CONTAINS, ranker.matchKind(searchQuery("tom"), "atomes"))
        assertEquals(MatchKind.FUZZY, ranker.matchKind(searchQuery("tomatte"), "tomates"))
        assertNull(ranker.matchKind(searchQuery("xyz"), "lait"))
    }

    @Test
    fun `exact match wins over a much more frequent prefix match`() {
        val names = rankNames("lait", product("Lait entier", useCount = 50, lastUsedAt = now), product("Lait"))
        assertEquals(listOf("Lait", "Lait entier"), names)
    }

    @Test
    fun `prefix beats word prefix which beats partial`() {
        val names = rankNames("tom", product("Atomes"), product("Sauce tomate"), product("Tomates"))
        assertEquals(listOf("Tomates", "Sauce tomate", "Atomes"), names)
    }

    @Test
    fun `frequent products come first inside the same match kind`() {
        val names =
            rankNames(
                "lai",
                product("Lait"),
                product("Lait entier", useCount = 3),
                product("Lait demi-écrémé", useCount = 12),
                product("Lait d'amande"),
            )
        assertEquals(listOf("Lait demi-écrémé", "Lait entier", "Lait", "Lait d'amande"), names)
    }

    @Test
    fun `recently used products come first when usage is equal`() {
        val names =
            rankNames(
                "tom",
                product("Tomates cerises", useCount = 2, lastUsedAt = now - 90 * day),
                product("Tomates concassées", useCount = 2, lastUsedAt = now - day),
            )
        assertEquals(listOf("Tomates concassées", "Tomates cerises"), names)
    }

    @Test
    fun `shorter names first when scores are equal`() {
        assertEquals(listOf("Tomates", "Tomates cerises", "Tomates concassées"), rankNames("tom", product("Tomates concassées"), product("Tomates cerises"), product("Tomates")))
    }

    @Test
    fun `aliases match but rank below an equivalent name match`() {
        val names = rankNames("oeuf", product("Œufs"), product("Omelette", aliases = listOf("oeufs battus")))
        assertEquals(listOf("Œufs", "Omelette"), names)
    }

    @Test
    fun `duplicates with the same normalized name are collapsed`() {
        val names = rankNames("tomates", product("Tomates", baseScore = 8), product("tomates", baseScore = 1))
        assertEquals(listOf("Tomates"), names)
    }

    @Test
    fun `non matching candidates are dropped and limit is applied`() {
        val names = rankNames("lai", product("Lait"), product("Laitue"), product("Pain"), limit = 1)
        assertEquals(listOf("Lait"), names)
        assertTrue(rankNames("zzz", product("Lait")).isEmpty())
    }

    @Test
    fun `ties on score and length are ordered alphabetically, ignoring case`() {
        assertEquals(listOf("Poire", "pomme", "Porto"), rankNames("po", product("Porto"), product("pomme"), product("Poire")))
    }

    @Test
    fun `the stored normalized forms are the ones compared`() {
        // The candidate is matched on what the catalog stored, never on its display name again.
        val stored = product("Lait").copy(normalizedName = "leche")
        assertEquals(listOf("Lait"), rankNames("lech", stored))
        assertTrue(rankNames("lait", stored).isEmpty())
    }
}
