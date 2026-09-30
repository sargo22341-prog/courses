package org.opensources.courses.core.database

import android.content.res.XmlResourceParser
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.opensources.courses.R

/**
 * The lists and the purchase history never leave the phone for a backup service, but follow the
 * user in a direct device transfer; the Home Assistant settings and token go nowhere.
 */
@RunWith(AndroidJUnit4::class)
class DataExtractionRulesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun databaseIsExcludedFromCloudBackupOnly() {
        val excluded = excludedPaths()
        val database = setOf("", "-journal", "-wal", "-shm").map { "database:${CoursesDatabase.NAME}$it" }

        assertTrue(excluded.getValue("cloud-backup").containsAll(database))
        assertTrue(excluded.getValue("device-transfer").none { it.startsWith("database:") })
    }

    @Test
    fun homeAssistantSettingsAndTokenAreExcludedEverywhere() {
        val homeAssistant = setOf("file:datastore/home_assistant.preferences_pb", "file:datastore/secrets.preferences_pb")

        excludedPaths().values.forEach { paths -> assertTrue(paths.containsAll(homeAssistant)) }
        assertEquals(setOf("cloud-backup", "device-transfer"), excludedPaths().keys)
    }

    /** `domain:path` of every excluded file, by section. */
    private fun excludedPaths(): Map<String, Set<String>> {
        val excluded = mutableMapOf<String, MutableSet<String>>()
        var section = ""
        context.resources.getXml(R.xml.data_extraction_rules).use { parser ->
            while (parser.next() != XmlResourceParser.END_DOCUMENT) {
                if (parser.eventType != XmlResourceParser.START_TAG) continue
                when (parser.name) {
                    "cloud-backup", "device-transfer" -> section = parser.name.also { excluded[it] = mutableSetOf() }
                    "exclude" -> excluded.getValue(section) += "${parser.getAttributeValue(null, "domain")}:${parser.getAttributeValue(null, "path")}"
                }
            }
        }
        return excluded
    }
}
