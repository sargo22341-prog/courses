package org.opensources.courses.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every visible text exists in every interface language, with the same placeholders: a string
 * forgotten in one language would silently show in English there.
 */
class StringResourcesTest {
    private val res = File("src/main/res")
    private val languages = listOf("de", "es", "fr", "it", "pt")

    private data class Text(
        val placeholders: List<String>,
        val translatable: Boolean,
    )

    private fun read(directory: String): Map<String, Text> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$directory/strings.xml"))
        val root = document.documentElement
        val elements = (0 until root.childNodes.length).map { root.childNodes.item(it) }.filterIsInstance<Element>()
        return elements.associate { element ->
            val key = "${element.tagName}:${element.getAttribute("name")}"
            key to Text(PLACEHOLDER.findAll(element.textContent).map { it.value }.toList().sorted(), element.getAttribute("translatable") != "false")
        }
    }

    @Test
    fun `every translatable text is translated with the same placeholders`() {
        val default = read("values")
        val translatable = default.filterValues { it.translatable }
        assertTrue(translatable.size > 100)
        languages.forEach { language ->
            val translated = read("values-$language")
            assertEquals("values-$language", translatable.keys, translated.keys)
            translatable.forEach { (key, text) ->
                assertEquals("$key in values-$language", text.placeholders, translated.getValue(key).placeholders)
            }
        }
    }

    @Test
    fun `the unqualified resources are the declared default language`() {
        assertEquals("unqualifiedResLocale=en-US", File(res, "resources.properties").readText().trim())
    }

    private companion object {
        val PLACEHOLDER = Regex("%(\\d+\\$)?[sd]")
    }
}
