package org.opensources.courses.feature.homeassistant.data.sync

import org.opensources.courses.feature.catalog.domain.TextNormalizer
import org.opensources.courses.feature.homeassistant.domain.HaItemFormat
import org.opensources.courses.feature.homeassistant.domain.HaTodoItem

/**
 * Remote items that local items without uid can take, matched by name. Each remote item is given at
 * most once; those already linked to a local item ([claimed]) are never given.
 *
 * @param items searched in this order.
 * @param format of their list: a Mealie item "2 Pain" is named "Pain".
 */
class UnclaimedRemoteItems(
    items: List<HaTodoItem>,
    claimed: Collection<String>,
    format: HaItemFormat,
) {
    private val candidates = items.map { Candidate(it, TextNormalizer.normalize(format.read(it).name), TextNormalizer.normalize(it.summary)) }
    private val claimed = claimed.toMutableSet()

    /**
     * The first unclaimed item named like [name], or whose text is [text] (the one sent for it), case,
     * accents and punctuation aside; now claimed.
     */
    fun claim(
        name: String,
        text: String = name,
    ): HaTodoItem? {
        val nameKey = TextNormalizer.normalize(name)
        val textKey = TextNormalizer.normalize(text)
        val match =
            candidates.firstOrNull { (item, itemName, itemText) -> (itemName == nameKey || itemText == textKey) && item.uid !in claimed }?.item
                ?: return null
        claimed += match.uid
        return match
    }

    private data class Candidate(
        val item: HaTodoItem,
        val name: String,
        val text: String,
    )
}
