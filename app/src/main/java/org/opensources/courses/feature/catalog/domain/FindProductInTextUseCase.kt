package org.opensources.courses.feature.catalog.domain

import org.opensources.courses.feature.language.domain.AppLanguageRepository
import javax.inject.Inject

/**
 * The bundled or OpenFoodFacts product a free text is about ("gousse ail" → Ail, "graines de sésame
 * ou selon le goût" → Sésame, known as "Graines de sésame"), looked up offline in the catalog: the
 * longest part of the text naming a product wins ([ProductNameWindows]), in the app language,
 * singular or plural ([CategoryNameKeys]). For one name, a product's own name wins over an alias,
 * then the most reliable source. Custom products are left out: a word of the text must not pick a
 * product the user typed for something else. Null when nothing fits.
 */
class FindProductInTextUseCase
    @Inject
    constructor(
        private val catalog: CatalogRepository,
        private val languages: AppLanguageRepository,
    ) {
        suspend operator fun invoke(text: String): String? {
            val language = languages.language.value
            val keysByWindow = ProductNameWindows.of(TextNormalizer.normalize(text)).map { CategoryNameKeys.of(it, language) }
            if (keysByWindow.isEmpty()) return null
            val keys = keysByWindow.flatten().toSet()
            val names = catalog.findByNormalizedNames(keys).map { Match(it, isAlias = false) }
            val aliases = catalog.findByNormalizedAliases(keys).map { Match(it, isAlias = true) }
            val byKey = (names + aliases).filter { it.product.source != CatalogSource.CUSTOM }.groupBy { it.product.normalizedName }
            return keysByWindow.firstNotNullOfOrNull { windowKeys ->
                windowKeys.firstNotNullOfOrNull { key -> byKey[key]?.minWithOrNull(BEST_MATCH_FIRST)?.product?.id }
            }
        }

        private class Match(
            val product: CatalogProductRef,
            val isAlias: Boolean,
        )

        private companion object {
            val BEST_MATCH_FIRST: Comparator<Match> = compareBy<Match> { it.isAlias }.thenBy { it.product.source.ordinal }
        }
    }
