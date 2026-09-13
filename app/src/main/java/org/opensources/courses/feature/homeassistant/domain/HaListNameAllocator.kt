package org.opensources.courses.feature.homeassistant.domain

import org.opensources.courses.feature.catalog.domain.TextNormalizer

/**
 * Name given to a list created in Home Assistant. Local To-do refuses a name already used by
 * another of its lists, and two lists with the same name are confusing anyway: a free number is
 * appended (« Courses », « Courses 2 », « Courses 3 »…). Names are compared ignoring case, accents
 * and punctuation, like Home Assistant does when it derives the list identifier.
 */
object HaListNameAllocator {
    fun uniqueName(
        name: String,
        takenNames: Collection<String>,
    ): String {
        val taken = takenNames.map(TextNormalizer::normalize).toSet()
        if (TextNormalizer.normalize(name) !in taken) return name
        return generateSequence(FIRST_SUFFIX) { it + 1 }
            .map { "$name $it" }
            .first { TextNormalizer.normalize(it) !in taken }
    }

    private const val FIRST_SUFFIX = 2
}
