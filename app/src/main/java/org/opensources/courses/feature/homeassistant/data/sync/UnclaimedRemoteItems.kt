package org.opensources.courses.feature.homeassistant.data.sync

import org.opensources.courses.feature.catalog.domain.TextNormalizer
import org.opensources.courses.feature.homeassistant.domain.HaTodoItem

/**
 * Remote items that local items without uid can take, matched by name. Each remote item is given at
 * most once; those already linked to a local item ([claimed]) are never given.
 *
 * @param items searched in this order.
 */
class UnclaimedRemoteItems(
    items: List<HaTodoItem>,
    claimed: Collection<String>,
) {
    private val candidates = items.map { it to TextNormalizer.normalize(it.summary) }
    private val claimed = claimed.toMutableSet()

    /** The first unclaimed item named like [name] (case, accents and punctuation aside), now claimed. */
    fun claim(name: String): HaTodoItem? {
        val key = TextNormalizer.normalize(name)
        val match = candidates.firstOrNull { (item, normalized) -> normalized == key && item.uid !in claimed }?.first ?: return null
        claimed += match.uid
        return match
    }
}
