package io.github.tournierjc.mercury.ui

import io.github.tournierjc.mercury.device.Languages
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every language says the same things as the base one.
 *
 * A missing key falls back to the base string at runtime — silently, on a phone set to that
 * language, which is the kind of defect nobody reports and nobody sees in a diff. The languages are
 * walked from [Languages], not from a list written here: adding a translation to the registry is
 * enough for the build to demand its keys, and nothing has to be remembered in this file.
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

    /** The base folder: `values/`, in the language the app was written in. */
    private val base: Map<String, String> by lazy { keys("values") }

    @Test
    fun `every language has every key of the base, and no key of its own`() {
        assertTrue("no strings found", base.isNotEmpty())
        for (language in Languages.translated) {
            val other = keys(language.resourceDir)
            assertEquals(
                "${language.tag}: keys missing in ${language.resourceDir}",
                emptySet<String>(),
                base.keys - other.keys,
            )
            assertEquals(
                "${language.tag}: keys that exist nowhere else",
                emptySet<String>(),
                other.keys - base.keys,
            )
        }
    }

    @Test
    fun `a translation keeps the format arguments of the original`() {
        val wrong = mutableListOf<String>()
        for (language in Languages.translated) {
            val other = keys(language.resourceDir)
            for ((key, value) in base) {
                val translated = other[key] ?: continue
                if (arguments(value) != arguments(translated)) {
                    wrong += "${language.tag} $key: '$value' vs '$translated'"
                }
            }
        }
        assertEquals("format arguments differ in translation", emptyList<String>(), wrong)
    }

    /** The `%1$s` / `%d` placeholders of a string, sorted, so reordering a sentence is allowed. */
    private fun arguments(text: String): List<String> =
        Regex("%(\\d+\\$)?[sd]").findAll(text).map { it.value }.sorted().toList()
}
