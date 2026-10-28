package io.github.tournierjc.mercury.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The permissions editor speaks two languages, and a missing sentence is a French word on an English
 * screen. Both files are new, so the check is parity rather than a list of prose: it fails when a key
 * is added to one locale only, or when a translation is left blank.
 */
class PermissionsStringsTest {

    private val root = File("src/main/res")

    private fun strings(locale: String): Map<String, String> {
        val resource = File(root, "$locale/strings_permissions.xml")
        assertTrue("missing ${resource.path}", resource.exists())
        val pattern = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        // Android escapes apostrophes in XML (`n\'a`); the test reads the words, not the escaping.
        return pattern.findAll(resource.readText())
            .associate { it.groupValues[1] to it.groupValues[2].replace("\\'", "'") }
    }

    @Test
    fun `every key exists in both locales`() {
        val fr = strings("values")
        val en = strings("values-en")

        assertTrue("the editor's strings are empty", fr.isNotEmpty())
        assertEquals("keys differ between FR and EN", fr.keys, en.keys)
    }

    @Test
    fun `no sentence is left blank or untranslated`() {
        val fr = strings("values")
        val en = strings("values-en")

        val blank = fr.filterValues { it.isBlank() }.keys + en.filterValues { it.isBlank() }.keys
        assertTrue("blank strings: $blank", blank.isEmpty())
        // The removal label carries the folder's name; every other key is prose and must actually
        // differ, or the English screen shows French.
        val identical = (fr.keys - setOf("bots_perm_cd_remove_path"))
            .filter { fr[it] == en[it] }
        assertTrue("same sentence in both locales: $identical", identical.isEmpty())
    }

    @Test
    fun `both locales take the same arguments`() {
        // A `%1$s` in one file and not the other renders as "Retirer" with no folder named — the
        // screen's mistake, not the translator's.
        val fr = strings("values")
        val en = strings("values-en")
        val placeholders = { text: String ->
            Regex("""%(\d+)\$[sd]""").findAll(text).map { it.value }.sorted().toList()
        }
        val mismatched = fr.keys.filter { placeholders(fr.getValue(it)) != placeholders(en.getValue(it)) }

        assertEquals("arguments differ between FR and EN: $mismatched", emptyList<String>(), mismatched)
    }

    @Test
    fun `the note names the two rules the daemon applies`() {
        // Deny beats allow, and `*` is refused in auto-approval: the only copy that says why a switch
        // looks ignored.
        assertTrue(strings("values").getValue("bots_perm_note").contains("*"))
        assertNotEquals(strings("values").getValue("bots_perm_note"), strings("values-en").getValue("bots_perm_note"))
    }
}
