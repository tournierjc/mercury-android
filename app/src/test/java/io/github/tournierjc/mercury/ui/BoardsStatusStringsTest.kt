package io.github.tournierjc.mercury.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The rules of SPECS/111 that live in the resources, not in a composable: six statuses spelled in
 * both locales, the counts as plurals, and the unavailable copy that keeps "I cannot see your
 * boards" from reading as "you have no boards".
 *
 * Values are asserted where a spec fixed them; elsewhere the test only demands that a translation
 * exists and differs from the other locale — pinning 78 sentences here would make every copy
 * decision a test failure.
 */
class BoardsStatusStringsTest {

    private val root = File("src/main/res")

    private fun strings(locale: String): Map<String, String> {
        val resource = File(root, "$locale/strings_boards_memory.xml")
        assertTrue("missing ${resource.path}", resource.exists())
        val pattern = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        // Android escapes apostrophes in XML (`n\'a`); the test reads the words, not the escaping.
        return pattern.findAll(resource.readText())
            .associate { it.groupValues[1] to it.groupValues[2].replace("\\'", "'") }
    }

    private fun plurals(locale: String): Map<String, Map<String, String>> {
        val resource = File(root, "$locale/strings_boards_memory.xml")
        val block = Regex("""<plurals name="([^"]+)">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL)
        return block.findAll(resource.readText()).associate { match ->
            val items = Regex("""<item quantity="([^"]+)">(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(match.groupValues[2])
                .associate { it.groupValues[1] to it.groupValues[2] }
            match.groupValues[1] to items
        }
    }

    private val statusKeys = listOf(
        "boards_statut_pending",
        "boards_statut_paused",
        "boards_statut_question",
        "boards_statut_running",
        "boards_statut_done",
        "boards_statut_failed",
    )

    @Test
    fun `the six statuses are spelled in both locales`() {
        val fr = strings("values")
        val en = strings("values-en")

        val missing = statusKeys.filterNot { it in fr && it in en }
        assertEquals("keys without a string: $missing", emptyList<String>(), missing)
        assertTrue("a status label is blank", statusKeys.none { fr[it].isNullOrBlank() || en[it].isNullOrBlank() })
    }

    @Test
    fun `the french labels carry an initial capital and no inner capitals`() {
        val fr = strings("values")

        statusKeys.forEach { key ->
            val value = fr.getValue(key)
            assertEquals("$key starts lowercase", value.first().uppercaseChar(), value.first())
            // "En Attente De Réponse" is title case translated; French does not do that.
            val inner = value.drop(1).filter { it.isUpperCase() }
            assertTrue("$key has inner capitals: $value", inner.isEmpty())
        }
    }

    @Test
    fun `the english column words are the webapp's, verbatim`() {
        val en = strings("values-en")

        assertEquals("To Do", en["boards_statut_pending"])
        assertEquals("In Progress", en["boards_statut_running"])
        assertEquals("Done", en["boards_statut_done"])
        assertEquals("Failed", en["boards_statut_failed"])
        assertEquals("Paused", en["boards_statut_paused"])
        assertEquals("Awaiting answer", en["boards_statut_question"])
    }

    @Test
    fun `every status reads differently in the two locales`() {
        val fr = strings("values")
        val en = strings("values-en")

        statusKeys.forEach { key ->
            assertNotEquals("$key is the same sentence in both locales", fr.getValue(key), en.getValue(key))
        }
    }

    @Test
    fun `counts are plurals with one and other, and the same argument in both`() {
        val fr = plurals("values")
        val en = plurals("values-en")

        listOf("boards_nb_cartes", "boards_sous_titre_nb_plateaux").forEach { key ->
            listOf("one", "other").forEach { quantity ->
                assertTrue("$key has no $quantity in fr", fr[key]?.containsKey(quantity) == true)
                assertTrue("$key has no $quantity in en", en[key]?.containsKey(quantity) == true)
                assertTrue("$key($quantity) lost %1\$d", fr.getValue(key).getValue(quantity).contains("%1\$d"))
                assertTrue("$key($quantity) lost %1\$d", en.getValue(key).getValue(quantity).contains("%1\$d"))
            }
        }
        // 0 takes the singular in French: that is the ICU rule, not a mistake.
        assertEquals("%1\$d plateau", fr.getValue("boards_sous_titre_nb_plateaux").getValue("one"))
    }

    @Test
    fun `the duplicate per-card count is gone`() {
        assertTrue(
            "boards_sous_titre_nb_cartes duplicates boards_nb_cartes",
            "boards_sous_titre_nb_cartes" !in strings("values"),
        )
    }

    @Test
    fun `the unavailable copy exists in both locales and says nothing was deleted`() {
        val fr = strings("values")
        val en = strings("values-en")

        listOf("boards_indisponible_titre", "boards_indisponible_corps", "boards_indisponible_reessayer")
            .forEach { key ->
                assertTrue("$key missing in fr", !fr[key].isNullOrBlank())
                assertTrue("$key missing in en", !en[key].isNullOrBlank())
            }
        assertTrue("fr body must deny an empty list", fr.getValue("boards_indisponible_corps").contains("rien n'a été supprimé"))
        assertTrue("en body must deny an empty list", en.getValue("boards_indisponible_corps").contains("nothing was deleted"))
    }
}
