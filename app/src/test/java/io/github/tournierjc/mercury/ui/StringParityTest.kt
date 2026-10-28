package io.github.tournierjc.mercury.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two languages say the same things.
 *
 * A missing French key falls back to the English string at runtime — silently, on a French phone,
 * which is the kind of defect nobody reports and nobody sees in a diff. The screens here are new
 * enough that this is the only guard: every key declared in `values/` must exist in `values-en/`,
 * with the same format arguments, or the build says so.
 */
class StringParityTest {

    private val res = File("src/main/res")

    private fun keys(dir: String): Map<String, String> {
        val files = File(res, dir).listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }
            ?: error("no $dir")
        val pattern = Regex("""<(string|plurals)\s+name="([^"]+)"[^>]*>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)
        return files.filter { it.isFile }
            .flatMap { file -> pattern.findAll(file.readText()).map { m -> m.groupValues[2] to m.groupValues[3] } }
            .toMap()
    }

    @Test
    fun `every French key exists in English, and the other way round`() {
        val fr = keys("values")
        val en = keys("values-en")
        assertTrue("no strings found", fr.isNotEmpty())
        assertEquals("keys missing in values-en", emptySet<String>(), fr.keys - en.keys)
        assertEquals("keys missing in values", emptySet<String>(), en.keys - fr.keys)
    }

    @Test
    fun `a translation keeps the format arguments of the original`() {
        val fr = keys("values")
        val en = keys("values-en")
        val wrong = mutableListOf<String>()
        for ((key, value) in fr) {
            val other = en[key] ?: continue
            if (arguments(value) != arguments(other)) wrong += "$key: '$value' vs '$other'"
        }
        assertEquals("format arguments differ in translation", emptyList<String>(), wrong)
    }

    /** The `%1$s` / `%d` placeholders of a string, sorted, so reordering a sentence is allowed. */
    private fun arguments(text: String): List<String> =
        Regex("%(\\d+\\$)?[sd]").findAll(text).map { it.value }.sorted().toList()
}
