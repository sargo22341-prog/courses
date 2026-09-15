package org.opensources.courses.feature.language.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLanguageTest {
    @Test
    fun `the device language is used when supported, whatever its region`() {
        assertEquals(AppLanguage.PORTUGUESE, AppLanguage.resolve(listOf("pt-BR")))
        assertEquals(AppLanguage.GERMAN, AppLanguage.resolve(listOf("de-AT", "fr-FR")))
        assertEquals(AppLanguage.FRENCH, AppLanguage.resolve(listOf("fr_CA")))
    }

    @Test
    fun `the first supported preferred language wins, English otherwise`() {
        assertEquals(AppLanguage.ITALIAN, AppLanguage.resolve(listOf("ja-JP", "it-IT", "es-ES")))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolve(listOf("ja-JP", "nl-NL")))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolve(emptyList()))
    }

    @Test
    fun `tags are read without their region and unknown tags give nothing`() {
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromTag(" ES-mx "))
        assertNull(AppLanguage.fromTag("pl"))
        assertNull(AppLanguage.fromTag(""))
    }

    @Test
    fun `languages are offered in the order of their own names`() {
        assertEquals(listOf("de", "en", "es", "fr", "it", "pt"), AppLanguage.entries.map { it.tag })
    }
}
