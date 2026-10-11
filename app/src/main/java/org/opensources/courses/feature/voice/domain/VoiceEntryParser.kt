package org.opensources.courses.feature.voice.domain

import org.opensources.courses.feature.language.domain.AppLanguage
import org.opensources.courses.feature.shopping.domain.ItemEntry
import org.opensources.courses.feature.shopping.domain.ItemEntryParser

/** Conservative splitting: unknown words remain editable rather than being guessed away. */
object VoiceEntryParser {
    const val MAX_TEXT = 4_000
    const val MAX_ITEMS = 50

    fun parse(text: String, language: AppLanguage): List<ItemEntry> {
        val grammar = grammar(language)
        val clean = text.take(MAX_TEXT).trim().lowercase(language.locale)
            .replace(grammar.prefix, "").replace(Regex("[.!?]+$"), "")
        val pieces = clean.split(grammar.separator).take(MAX_ITEMS * 2)
        val joined = mutableListOf<String>()
        for (part in pieces) {
            val previous = joined.lastOrNull()
            if (previous != null && ItemEntryParser.isMeasuredQuantity(SpokenQuantityParser.replaceLeading(previous, language))) {
                joined[joined.lastIndex] = "$previous $part"
            } else {
                joined.add(part)
            }
        }
        val measured = joined.flatMap { VoiceMeasuredEntrySplitter.split(it, language) }
        return measured.take(MAX_ITEMS).mapNotNull { part ->
            val name = part.trim().replace(grammar.article, "")
            if (name.isBlank()) return@mapNotNull null
            val entry = ItemEntryParser.parse(SpokenQuantityParser.replaceLeading(name, language))
            entry.copy(name = entry.name.replace(grammar.article, "")).takeIf { it.name.isNotBlank() }
        }
    }

    private fun grammar(language: AppLanguage): Grammar = when (language) {
        AppLanguage.FRENCH -> Grammar(
            "^(?:(?:je voudrais|je veux|ajoute|ajouter|il me faut|j['’]aimerais)\\s+)?(?:(?:dans|sur|à)\\s+)?(?:(?:ma|la|une)\\s+liste(?:\\s+de courses)?\\s*)?",
            "(?<!vingt|trente|quarante|cinquante|soixante)\\s+et\\s+|\\s+(?=(?:(?:du|des|de la)\\s+|de l['’]))|(?<!\\d)[,;]|[,;](?!\\d)",
            "^(?:(?:de la|du|des|de|le|la|les)\\s+|(?:de l|d|l)['’]\\s*)",
        )
        AppLanguage.ENGLISH -> Grammar("^(?:(?:i would like|i want|add|i need)\\s+)?(?:in my shopping list\\s+)?", "\\s+and\\s+|(?<!\\d)[,;]|[,;](?!\\d)", "^(?:some|the|of)\\s+")
        AppLanguage.GERMAN -> Grammar("^(?:(?:ich möchte|ich brauche|füge hinzu)\\s+)?", "\\s+und\\s+|(?<!\\d)[,;]|[,;](?!\\d)", "^(?:der|die|das)\\s+")
        AppLanguage.SPANISH -> Grammar("^(?:(?:quiero|quisiera|añade|necesito)\\s+)?", "\\s+y\\s+|(?<!\\d)[,;]|[,;](?!\\d)", "^(?:el|la|los|las|de)\\s+")
        AppLanguage.ITALIAN -> Grammar("^(?:(?:vorrei|voglio|aggiungi)\\s+)?", "\\s+e\\s+|(?<!\\d)[,;]|[,;](?!\\d)", "^(?:del|della|dei|delle|di)\\s+")
        AppLanguage.PORTUGUESE -> Grammar("^(?:(?:quero|gostaria de|adiciona|preciso de)\\s+)?", "\\s+e\\s+|(?<!\\d)[,;]|[,;](?!\\d)", "^(?:do|da|dos|das|de)\\s+")
    }

    private class Grammar(prefix: String, separator: String, article: String) {
        val prefix = Regex(prefix)
        val separator = Regex(separator)
        val article = Regex(article)
    }
}
