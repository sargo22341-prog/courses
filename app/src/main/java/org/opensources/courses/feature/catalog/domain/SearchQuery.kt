package org.opensources.courses.feature.catalog.domain

/**
 * A query already in [TextNormalizer] form, split into words once for all the candidates it is
 * compared with: ranking thousands of products must not split it again for each of them.
 */
class SearchQuery(
    val text: String,
) {
    val words: List<String> = TextNormalizer.words(text)
}
