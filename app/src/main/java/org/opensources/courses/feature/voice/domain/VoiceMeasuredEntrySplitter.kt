package org.opensources.courses.feature.voice.domain

import org.opensources.courses.feature.language.domain.AppLanguage
import org.opensources.courses.feature.shopping.domain.ItemEntryParser

/** Speech engines sometimes omit a conjunction; an explicit leading quantity still marks a new item. */
object VoiceMeasuredEntrySplitter {
    fun split(text: String, language: AppLanguage): List<String> {
        val quantityWords = SpokenQuantityParser.vocabulary(language) + ItemEntryParser.spokenUnits() + LINKS
        val rows = mutableListOf<String>()
        var start = 0
        for (word in WORD.findAll(text).take(600).drop(1)) {
            if (rows.size >= VoiceEntryParser.MAX_ITEMS - 1) break
            val position = word.range.first
            val measured = ItemEntryParser.parse(SpokenQuantityParser.replaceLeading(text.substring(position), language))
            if (measured.quantity == null) continue
            val previous = text.substring(start, position).trim()
            val entry = ItemEntryParser.parse(SpokenQuantityParser.replaceLeading(previous, language))
            if (entry.name.isBlank() || WORD.findAll(entry.name).all { it.value in quantityWords }) continue
            rows.add(previous)
            start = position
        }
        rows.add(text.substring(start).trim())
        return rows
    }

    private val WORD = Regex("\\S+")
    private val LINKS = setOf("virgule", "et", "point", "and", "komma", "und", "coma", "y", "virgola", "e", "vírgula", "de", "of", "di", "du", "des")
}
