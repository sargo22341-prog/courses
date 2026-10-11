package org.opensources.courses.feature.voice.domain

import org.opensources.courses.feature.catalog.domain.BundledCatalogSource
import org.opensources.courses.feature.catalog.domain.WordForms
import org.opensources.courses.feature.language.domain.AppLanguage
import org.opensources.courses.feature.shopping.domain.ItemEntryParser
import javax.inject.Inject

/** Guide the acoustic engine with shipped foods; never use purchase history or remote data. */
class VoiceVocabularyUseCase @Inject constructor(private val sources: List<@JvmSuppressWildcards BundledCatalogSource>) {
    suspend operator fun invoke(language: AppLanguage): List<String> {
        val phrases = linkedSetOf("[unk]")
        phrases.addAll(SpokenQuantityParser.vocabulary(language))
        phrases.addAll(ItemEntryParser.spokenUnits())
        phrases.addAll(connectors(language).split('|'))
        val essentials = phrases.toList()
        val commonNames = linkedSetOf<String>()
        for (source in sources.take(2)) {
            for (product in source.load(language).take(10_000)) {
                val name = cleanName(product.name, language)
                if (source.source == org.opensources.courses.feature.catalog.domain.CatalogSource.SEED && name.isNotBlank()) {
                    commonNames.add(name)
                    val words = name.split(' ')
                    commonNames.add((listOf(WordForms.of(language).singular(words.first())) + words.drop(1)).joinToString(" "))
                }
                if (phrases.size < MAX_PHRASES) phrases.add(name)
                for (alias in product.aliases.take(20)) {
                    if (phrases.size < MAX_PHRASES) phrases.add(cleanName(alias, language))
                }
            }
        }
        return contextualVocabulary(essentials, phrases.filter(String::isNotBlank), commonNames, language)
    }

    private fun cleanName(name: String, language: AppLanguage): String =
        WORD.findAll(name.lowercase(language.locale)).take(20).joinToString(" ") { it.value }

    private fun contextualVocabulary(essentials: List<String>, base: List<String>, names: Set<String>, language: AppLanguage): List<String> {
        val phrases = linkedSetOf<String>()
        phrases.addAll(essentials)
        val conjunction = when (language) {
            AppLanguage.FRENCH -> "et"
            AppLanguage.ENGLISH -> "and"
            AppLanguage.GERMAN -> "und"
            AppLanguage.SPANISH -> "y"
            AppLanguage.ITALIAN -> "e"
            AppLanguage.PORTUGUESE -> "e"
        }
        // Distribute each quantity across the whole seed before the next quantity. Otherwise
        // earlier foods exhaust the budget before household products such as bin bags.
        for (prefix in quantityPrefixes(language).split('|')) for (name in names) {
            phrases.add(name)
            phrases.add("$prefix $name")
            phrases.add("$conjunction $prefix $name")
            if (phrases.size >= MAX_PHRASES - 2) return phrases.take(MAX_PHRASES)
        }
        for (phrase in base) {
            if (phrases.size >= MAX_PHRASES) break
            phrases.add(phrase)
        }
        return phrases.toList()
    }

    private fun connectors(language: AppLanguage): String = when (language) {
        AppLanguage.FRENCH -> "je voudrais|je veux|il me faut|ajoute|dans ma liste de courses|et|virgule|demi"
        AppLanguage.ENGLISH -> "i would like|i want|i need|add|in my shopping list|and|point|half"
        AppLanguage.GERMAN -> "ich möchte|ich brauche|füge hinzu|auf meine einkaufsliste|und|komma|halb"
        AppLanguage.SPANISH -> "quiero|quisiera|añade|necesito|en mi lista de compras|y|coma|medio"
        AppLanguage.ITALIAN -> "vorrei|voglio|aggiungi|nella mia lista della spesa|e|virgola|mezzo"
        AppLanguage.PORTUGUESE -> "quero|gostaria|preciso|adiciona|na minha lista de compras|e|vírgula|meio"
    }

    private fun quantityPrefixes(language: AppLanguage): String = when (language) {
        AppLanguage.FRENCH -> "du|de|des|un|une|deux|trois|quatre|cinq|six|sept|huit|neuf|dix|cent grammes de|deux cents grammes de|deux cent cinquante grammes de|cinq cents grammes de|un kilo de|deux kilos de|un litre de|un virgule cinq litres de"
        AppLanguage.ENGLISH -> "some|one|two|three|four|five|six|seven|eight|nine|ten|five hundred grams of|one kilogram of|one liter of"
        AppLanguage.GERMAN -> "ein|eine|zwei|drei|vier|fünf|sechs|sieben|acht|neun|zehn|fünfhundert gramm|ein kilo|ein liter"
        AppLanguage.SPANISH -> "un|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|quinientos gramos de|un kilo de|un litro de"
        AppLanguage.ITALIAN -> "un|una|due|tre|quattro|cinque|sei|sette|otto|nove|dieci|cinquecento grammi di|un chilo di|un litro di"
        AppLanguage.PORTUGUESE -> "um|uma|dois|três|quatro|cinco|seis|sete|oito|nove|dez|quinhentos gramas de|um quilo de|um litro de"
    }

    private companion object {
        val WORD = Regex("[\\p{L}]+")
        const val MAX_PHRASES = 16_000
    }
}
