package io.github.tournierjc.mercury.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The strings of the memory and retention sections, one file per locale.
 *
 * The daemon's vocabulary travels: a label the code asks for but no locale spells is a crash at
 * draw time, and a French sentence reaching an English screen was the bug that made every other
 * strings file carry this test.
 */
class BotConfigStringsTest {

    private val root = File("src/main/res")

    private fun strings(locale: String): Map<String, String> {
        val resource = File(root, "$locale/strings_botconfig.xml")
        assertTrue("missing ${resource.path}", resource.exists())
        val pattern = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        // Android escapes apostrophes in XML (`n\'a`); the test reads the words, not the escaping.
        return pattern.findAll(resource.readText())
            .associate { it.groupValues[1] to it.groupValues[2].replace("\\'", "'") }
    }

    @Test
    fun `every key is spelled in both locales`() {
        val fr = strings("values")
        val en = strings("values-en")

        assertEquals("keys only in one locale", fr.keys, en.keys)
        assertTrue("a label is blank", fr.values.none { it.isBlank() } && en.values.none { it.isBlank() })
    }

    @Test
    fun `the two locales say it differently`() {
        val fr = strings("values")
        val en = strings("values-en")

        fr.forEach { (key, text) -> assertNotEquals("$key is the French sentence in both files", text, en.getValue(key)) }
    }

    @Test
    fun `the sections and the fifth limit are named`() {
        val fr = strings("values")
        val en = strings("values-en")

        listOf(
            "bots_section_memory",
            "bots_section_retention",
            "bots_label_max_concurrent",
            "bots_config_default",
        ).forEach { key ->
            assertTrue("$key missing in fr", fr[key]?.isNotBlank() == true)
            assertTrue("$key missing in en", en[key]?.isNotBlank() == true)
        }
        // An empty field means the daemon's default: the placeholder has to say so, in words.
        assertTrue(fr.getValue("bots_config_default").contains("défaut"))
        assertTrue(en.getValue("bots_config_default").contains("default"))
    }

    @Test
    fun `the three scopes are spelled, and none of them asks for an argument`() {
        val fr = strings("values")
        val en = strings("values-en")

        val scopes = listOf("none", "own", "shared")
        scopes.forEach { scope ->
            assertTrue("bots_memory_scope_$scope missing", "bots_memory_scope_$scope" in fr && "bots_memory_scope_$scope" in en)
            assertTrue("a scope has no sentence", "bots_memory_scope_${scope}_verb" in fr && "bots_memory_scope_${scope}_verb" in en)
        }
        // These strings are drawn with `stringResource(id)` and no arguments: a placeholder would
        // print as `%1$s` on the screen.
        val offending = (fr + en).filterValues { text -> Regex("""%\d+\$[sd]""").containsMatchIn(text) }
        assertEquals("placeholders in a label: ${offending.keys}", emptyMap<String, String>(), offending)
    }
}
