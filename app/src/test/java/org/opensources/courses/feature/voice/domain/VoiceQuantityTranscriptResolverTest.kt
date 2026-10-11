package org.opensources.courses.feature.voice.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.courses.feature.language.domain.AppLanguage
import org.opensources.courses.feature.shopping.domain.ItemEntry

class VoiceQuantityTranscriptResolverTest {
    private val french = AppLanguage.FRENCH

    @Test fun recoversTwoOnlyWhenTheSecondDecodingActuallyHeardTwo() {
        val resolved = VoiceQuantityTranscriptResolver.resolve("de sacs poubelle", "deux sacs poubelle", french)
        assertEquals(listOf(ItemEntry("sacs poubelle", 2.0)), VoiceEntryParser.parse(resolved, french))
    }

    @Test fun doesNotInventTwoFromAnArticle() {
        assertEquals("de sacs poubelle", VoiceQuantityTranscriptResolver.resolve("de sacs poubelle", "des sacs poubelle", french))
    }

    @Test fun ignoresDifferentProductsAndAmbiguousDuplicates() {
        val guided = "de sacs poubelle"
        assertEquals(guided, VoiceQuantityTranscriptResolver.resolve(guided, "deux pommes", french))
        assertEquals(guided, VoiceQuantityTranscriptResolver.resolve(guided, "deux sacs poubelle et trois sacs poubelle", french))
        val repeated = "de sacs poubelle et de sacs poubelle"
        assertEquals(repeated, VoiceQuantityTranscriptResolver.resolve(repeated, "deux sacs poubelle", french))
    }

    @Test fun preservesRecognizedCountsAndUnknownWords() {
        val guided = "trois sacs poubelle et [unk]"
        assertEquals(guided, VoiceQuantityTranscriptResolver.resolve(guided, "deux sacs poubelle et deux pommes", french))
        assertFalse(VoiceQuantityTranscriptResolver.needsQuantityCheck(guided, french))
    }

    @Test fun matchesSingularAndPluralAndPreservesOtherRows() {
        val resolved = VoiceQuantityTranscriptResolver.resolve(
            "de sacs poubelle et cinq cents grammes de riz", "deux sac poubelle et du riz", french,
        )
        assertEquals(
            listOf(ItemEntry("sacs poubelle", 2.0), ItemEntry("riz", 500.0, "g")),
            VoiceEntryParser.parse(resolved, french),
        )
        assertTrue(VoiceQuantityTranscriptResolver.needsQuantityCheck("de sacs poubelle", french))
    }

    @Test fun supportsTheSixLanguages() {
        val names = mapOf(
            AppLanguage.FRENCH to "sacs poubelle", AppLanguage.ENGLISH to "bin bags",
            AppLanguage.GERMAN to "müllbeutel", AppLanguage.SPANISH to "bolsas de basura",
            AppLanguage.ITALIAN to "sacchi della spazzatura", AppLanguage.PORTUGUESE to "sacos do lixo",
        )
        for ((language, name) in names) {
            val result = VoiceQuantityTranscriptResolver.resolve(name, "2 $name", language)
            assertEquals(2.0, VoiceEntryParser.parse(result, language).single().quantity)
        }
    }
}
