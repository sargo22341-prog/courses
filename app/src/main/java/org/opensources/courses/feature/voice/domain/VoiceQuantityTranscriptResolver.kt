package org.opensources.courses.feature.voice.domain

import org.opensources.courses.feature.catalog.domain.CategoryNameKeys
import org.opensources.courses.feature.catalog.domain.TextNormalizer
import org.opensources.courses.feature.language.domain.AppLanguage
import org.opensources.courses.feature.shopping.domain.ItemEntry

/** Recover a missing quantity only when a second acoustic decoding identifies the same product. */
object VoiceQuantityTranscriptResolver {
    fun needsQuantityCheck(text: String, language: AppLanguage): Boolean =
        VoiceEntryParser.parse(text, language).any { it.quantity == null && !it.name.contains("[unk]") }

    fun resolve(guided: String, unrestricted: String, language: AppLanguage): String {
        val rows = VoiceEntryParser.parse(guided, language)
        val candidates = VoiceEntryParser.parse(unrestricted, language)
        var changed = false
        val resolved = rows.map { row ->
            if (row.quantity != null || row.name.contains("[unk]")) return@map row
            val keys = nameKeys(row, language)
            if (rows.count { nameKeys(it, language).any(keys::contains) } != 1) return@map row
            val candidate = candidates.singleOrNull { nameKeys(it, language).any(keys::contains) }
            if (candidate?.quantity == null) return@map row
            changed = true
            row.copy(quantity = candidate.quantity, unit = candidate.unit)
        }
        if (!changed) return guided
        return resolved.joinToString(" ; ") { row ->
            val quantity = row.quantity?.toString()?.removeSuffix(".0")
            if (quantity == null) row.name else "$quantity ${row.unit?.let { "$it " }.orEmpty()}${row.name}"
        }.take(VoiceEntryParser.MAX_TEXT)
    }

    private fun nameKeys(entry: ItemEntry, language: AppLanguage): List<String> =
        CategoryNameKeys.of(TextNormalizer.normalize(entry.name), language)
}
