package io.github.tournierjc.mercury.device

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The registry and the resource folders are the same list, seen twice.
 *
 * Adding a language means copying `values/` to `values-xx/` and adding one row to [Languages] — and
 * the failure mode of getting one of the two wrong is silent: a folder with no row is a translation
 * nobody can select, and a row with no folder is a choice that silently falls back to French. These
 * tests make both loud, so the next language really is two steps.
 */
class LanguagesTest {

    private val res = File("src/main/res")

    @Test
    fun `every translated language has the resource folder the registry promises`() {
        val missing = Languages.translated.filterNot { File(res, it.resourceDir).isDirectory }
        assertEquals("registered languages with no resource folder", emptyList<AppLanguage>(), missing)
    }

    @Test
    fun `every language folder is registered, so a translation can be selected`() {
        val folders = res.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }
            ?.map { it.name.substringAfter("values-") }
            ?.toSet()
            .orEmpty()
        // The base language owns `values/` and has no `values-fr/` sibling, so it is not expected
        // among the suffixed folders.
        val registered = Languages.translated.filterNot { it.isBase }.map { it.tag.lowercase() }.toSet()

        assertEquals("folders nobody can select", emptySet<String>(), folders - registered)
        assertEquals("registered tags with no folder", emptySet<String>(), registered - folders)
    }

    @Test
    fun `the picker offers the phone first, then one row per language`() {
        assertEquals(0, Languages.all.first().tag.length)
        assertTrue(Languages.all.first().followsSystem)
        assertEquals(Languages.all.size - 1, Languages.translated.size)
        // The native name is what the picker draws, so it cannot be blank.
        assertTrue(Languages.translated.none { it.nativeName.isBlank() })
    }

    @Test
    fun `exactly one language owns the base folder`() {
        val base = Languages.translated.filter { it.isBase }
        assertEquals("the base folder is written in one language", 1, base.size)
        assertEquals("values", base.single().resourceDir)
    }

    @Test
    fun `a stored tag we no longer ship reads as follow the phone`() {
        assertTrue(Languages.byTag("it").followsSystem)
        assertTrue(Languages.byTag("").followsSystem)
        assertTrue(Languages.byTag(null).followsSystem)
    }

    @Test
    fun `a stored tag matches whatever its case or spacing`() {
        assertEquals("en", Languages.byTag(" EN ").tag)
        assertEquals("fr", Languages.byTag("Fr").tag)
    }
}
