package org.opensources.courses.feature.voice.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opensources.courses.feature.language.domain.AppLanguage
import org.opensources.courses.feature.shopping.domain.ItemEntry

class VoiceEntryParserTest {
    @Test fun parsesFullFrenchRequest() {
        assertEquals(
            listOf(ItemEntry("pâtes", 500.0, "g"), ItemEntry("yaourt"), ItemEntry("café"), ItemEntry("céréales")),
            VoiceEntryParser.parse("Je voudrais dans ma liste de courses 500 g de pâtes du yaourt du café et des céréales", AppLanguage.FRENCH),
        )
    }

    @Test fun parsesShortRequest() {
        assertEquals(listOf(ItemEntry("café"), ItemEntry("yaourts")), VoiceEntryParser.parse("de café et de yaourts", AppLanguage.FRENCH))
    }

    @Test fun keepsDecimalCommaAndCompoundNames() {
        assertEquals(
            listOf(ItemEntry("pommes de terre", 1.5, "kg"), ItemEntry("café au lait")),
            VoiceEntryParser.parse("1,5 kg de pommes de terre et du café au lait", AppLanguage.FRENCH),
        )
    }

    @Test fun convertsSimpleSpokenCounts() {
        assertEquals(listOf(ItemEntry("yaourts", 2.0)), VoiceEntryParser.parse("deux yaourts", AppLanguage.FRENCH))
    }

    @Test fun parsesFrenchQuantitiesReturnedAsWordsByOfflineEngine() {
        assertEquals(
            listOf(ItemEntry("pâtes", 500.0, "g"), ItemEntry("yaourts", 2.0), ItemEntry("lait", 1.5, "L")),
            VoiceEntryParser.parse("cinq cents grammes de pâtes et deux yaourts et un virgule cinq litres de lait", AppLanguage.FRENCH),
        )
    }

    @Test fun doesNotSplitMeasuresFromArticlesOrProductPrefixes() {
        assertEquals(
            listOf(ItemEntry("riz", 500.0, "g"), ItemEntry("lait", 1.0, "L"), ItemEntry("durian")),
            VoiceEntryParser.parse("500 g du riz et un litre de lait et du durian", AppLanguage.FRENCH),
        )
    }

    @Test fun keepsFrenchNumberConjunctionAndHundredsTogether() {
        assertEquals(
            listOf(ItemEntry("yaourts", 21.0), ItemEntry("riz", 1_500.0, "g")),
            VoiceEntryParser.parse("vingt et un yaourts et mille cinq cents grammes de riz", AppLanguage.FRENCH),
        )
    }

    @Test fun recoversNewMeasureWhenSpeechEngineOmitsConjunction() {
        assertEquals(
            listOf(ItemEntry("café"), ItemEntry("yaourts", 3.0), ItemEntry("pâtes", 500.0, "g")),
            VoiceEntryParser.parse("je voudrais du café et trois yaourts cinq cents grammes de pâtes", AppLanguage.FRENCH),
        )
    }

    @Test fun splitsSpokenCountsWithoutConjunction() {
        assertEquals(
            listOf(ItemEntry("pommes", 2.0), ItemEntry("yaourts", 3.0), ItemEntry("bananes", 4.0)),
            VoiceEntryParser.parse("deux pommes trois yaourts quatre bananes", AppLanguage.FRENCH),
        )
    }

    @Test fun acceptsHyphenatedFrenchNumbers() {
        assertEquals(
            listOf(ItemEntry("yaourts", 21.0), ItemEntry("pommes", 90.0)),
            VoiceEntryParser.parse("vingt-et-un yaourts et quatre-vingt-dix pommes", AppLanguage.FRENCH),
        )
    }

    @Test fun keepsAllSpokenDecimalDigits() {
        assertEquals(
            listOf(ItemEntry("lait", 1.25, "L")),
            VoiceEntryParser.parse("un virgule deux cinq litres de lait", AppLanguage.FRENCH),
        )
    }

    @Test fun keepsUnknownNamesForReview() {
        assertEquals(listOf(ItemEntry("produit mystérieux")), VoiceEntryParser.parse("produit mystérieux", AppLanguage.FRENCH))
    }

    @Test fun keepsHyphensInProductNames() {
        assertEquals(
            listOf(ItemEntry("essuie-tout", 2.0)),
            VoiceEntryParser.parse("deux essuie-tout", AppLanguage.FRENCH),
        )
    }

    @Test fun quantityWithoutProductStaysReviewable() {
        assertEquals("quatre vingt", SpokenQuantityParser.replaceLeading("quatre vingt", AppLanguage.FRENCH))
    }

    @Test fun boundsInputAndRowCount() {
        assertEquals(50, VoiceEntryParser.parse(List(100) { "café" }.joinToString(" et "), AppLanguage.FRENCH).size)
        assertTrue(VoiceEntryParser.parse("a".repeat(8_000), AppLanguage.FRENCH).single().name.length <= 4_000)
        assertTrue(VoiceEntryParser.parse(" ", AppLanguage.FRENCH).isEmpty())
    }

    @Test fun supportsAllInterfaceLanguages() {
        val examples = mapOf(
            AppLanguage.ENGLISH to "I need 500 grams of pasta and coffee",
            AppLanguage.GERMAN to "Ich brauche 500 Gramm Nudeln und Kaffee",
            AppLanguage.SPANISH to "Quiero 500 gramos de pasta y café",
            AppLanguage.ITALIAN to "Vorrei 500 grammi di pasta e caffè",
            AppLanguage.PORTUGUESE to "Quero 500 gramas de massa e café",
        )
        for ((language, phrase) in examples) {
            val entries = VoiceEntryParser.parse(phrase, language)
            assertEquals(2, entries.size)
            assertEquals(500.0, entries.first().quantity)
            assertEquals("g", entries.first().unit)
        }
    }
}
