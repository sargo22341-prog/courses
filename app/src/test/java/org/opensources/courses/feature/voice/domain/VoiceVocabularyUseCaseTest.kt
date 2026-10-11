package org.opensources.courses.feature.voice.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.courses.feature.catalog.domain.BundledCatalogSource
import org.opensources.courses.feature.catalog.domain.CatalogImportProduct
import org.opensources.courses.feature.catalog.domain.CatalogSource
import org.opensources.courses.feature.language.domain.AppLanguage

class VoiceVocabularyUseCaseTest {
    @Test fun keepsCountsForLateProductsWhenContextBudgetIsFull() = runTest {
        val source = object : BundledCatalogSource {
            override val source = CatalogSource.SEED
            override val version = 1
            override suspend fun load(language: AppLanguage) =
                List(400) { index -> CatalogImportProduct("food$index", "Aliments ${letters(index)}", null, 1) } +
                    CatalogImportProduct("bags", "Sacs poubelle", null, 1)
        }
        val words = VoiceVocabularyUseCase(listOf(source))(AppLanguage.FRENCH)
        assertTrue(words.size <= 16_000)
        assertTrue(words.containsAll(listOf("deux sac poubelle", "deux sacs poubelle", "virgule", "[unk]")))
    }

    private fun letters(index: Int): String = "${('a'.code + index / 26).toChar()}${('a'.code + index % 26).toChar()}"

    @Test fun includesFoodPhrasesAliasesQuantitiesAndUnknownWords() = runTest {
        val source = object : BundledCatalogSource {
            override val source = CatalogSource.SEED
            override val version = 1
            override suspend fun load(language: AppLanguage) = listOf(
                CatalogImportProduct("coffee", "Café", null, 1),
                CatalogImportProduct("yogurt", "Yaourts nature", null, 1, aliases = listOf("Yogourt")),
                CatalogImportProduct("bags", "Sacs poubelle", null, 1),
            )
        }
        val words = VoiceVocabularyUseCase(listOf(source))(AppLanguage.FRENCH)
        assertTrue(words.containsAll(listOf("café", "yaourts nature", "yogourt", "cinq", "cents", "grammes", "je voudrais", "[unk]")))
        assertTrue(words.containsAll(listOf("et trois yaourts nature", "cinq cents grammes de café")))
        assertTrue(words.containsAll(listOf("deux sac poubelle", "deux sacs poubelle")))
    }
}
