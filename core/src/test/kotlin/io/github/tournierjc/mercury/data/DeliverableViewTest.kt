package io.github.tournierjc.mercury.data

import io.github.tournierjc.mercury.data.dto.DeliverableDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Searching and filtering the fleet's deliverables.
 *
 * Seventy-one files from ten bots is the list the owner actually has; these tests pin the three
 * answers he asked for — a word, a kind, a bot — and the fact that they compose instead of
 * overriding each other.
 */
class DeliverableViewTest {

    private fun file(name: String, bot: String = "veille-dgx", path: String = "", mtime: Long = 0) =
        DeliverableDto(botId = bot, name = name, bytes = 10, mtimeMs = mtime, path = path.ifBlank { "/out/$bot/$name" })

    private val files = listOf(
        file("Graphe du cerveau — fiche design.md", mtime = 300),
        file("probe.md", mtime = 100),
        file("capture.png", bot = "veille-colombages", mtime = 200),
        file("notes.txt", bot = "veille-portasplit", mtime = 50),
        file("archive.bin", bot = "veille-portasplit", mtime = 10),
    )

    @Test
    fun `an empty filter keeps everything`() {
        assertEquals(files, files.filteredBy(DeliverableFilter()))
        assertTrue(DeliverableFilter().isOff)
    }

    @Test
    fun `a word matches the name, whatever the case`() {
        val found = files.filteredBy(DeliverableFilter(query = "PROBE"))

        assertEquals(listOf("probe.md"), found.map { it.name })
        assertFalse(DeliverableFilter(query = "probe").isOff)
    }

    @Test
    fun `a word finds an accented name typed without accents`() {
        // A phone keyboard is not where anyone types "cervéau" with the accent in place.
        val found = files.filteredBy(DeliverableFilter(query = "cerveau"))

        assertEquals(listOf("Graphe du cerveau — fiche design.md"), found.map { it.name })
    }

    @Test
    fun `a word matches the folder the file sits in`() {
        val found = files.filteredBy(DeliverableFilter(query = "veille-colombages"))

        assertEquals(listOf("capture.png"), found.map { it.name })
    }

    @Test
    fun `the kind narrows to the extension family`() {
        assertEquals(listOf("capture.png"), files.filteredBy(DeliverableFilter(kind = OutputKind.Image)).map { it.name })
        assertEquals(
            listOf("Graphe du cerveau — fiche design.md", "probe.md", "notes.txt"),
            files.filteredBy(DeliverableFilter(kind = OutputKind.Text)).map { it.name },
        )
        assertEquals(listOf("archive.bin"), files.filteredBy(DeliverableFilter(kind = OutputKind.Binary)).map { it.name })
    }

    @Test
    fun `the bot narrows to its own files`() {
        val found = files.filteredBy(DeliverableFilter(botId = "veille-portasplit"))

        assertEquals(listOf("archive.bin", "notes.txt"), found.map { it.name }.sorted())
    }

    @Test
    fun `the three compose`() {
        val found = files.filteredBy(DeliverableFilter(query = "o", kind = OutputKind.Text, botId = "veille-portasplit"))

        // "notes.txt" is the only text file of that bot holding an "o" in its path or name.
        assertEquals(listOf("notes.txt"), found.map { it.name })
    }

    @Test
    fun `a word with nothing behind it leaves an empty list, not everything`() {
        assertEquals(emptyList<DeliverableDto>(), files.filteredBy(DeliverableFilter(query = "zzz")))
    }

    @Test
    fun `groups are alphabetical by bot name, newest file first inside each`() {
        val names = mapOf(
            "veille-dgx" to "Veille DGX",
            "veille-colombages" to "Veille colombages",
            "veille-portasplit" to "Veille portasplit",
        )
        val groups = files.groupedByBot(names)

        assertEquals(listOf("Veille colombages", "Veille DGX", "Veille portasplit"), groups.map { it.name })
        assertEquals(listOf("Graphe du cerveau — fiche design.md", "probe.md"), groups[1].files.map { it.name })
    }

    @Test
    fun `a bot the roster no longer knows keeps its files, after the others`() {
        val names = mapOf(
            "veille-dgx" to "Veille DGX",
            "veille-colombages" to "Veille colombages",
            "veille-portasplit" to "Veille portasplit",
        )
        val groups = files.plus(file("vieux.md", bot = "bot-supprime")).groupedByBot(names)

        assertEquals("bot-supprime", groups.last().botId)
        // Named by its identifier rather than by nothing: the file still exists.
        assertEquals("bot-supprime", groups.last().name)
    }

    @Test
    fun `the bot picker finds a name by a fragment, without case or accents`() {
        val groups = listOf("Œil", "Gardien", "Mercury Android").map { DeliverableGroup(it, it, emptyList()) }

        assertEquals(listOf("Œil"), groups.matchingBots("oeil").map { it.name })
        assertEquals(listOf("Gardien"), groups.matchingBots("  GARDIEN ").map { it.name })
        assertEquals(listOf("Mercury Android"), groups.matchingBots("android").map { it.name })
        assertTrue(groups.matchingBots("zzz").isEmpty())
    }

    @Test
    fun `an empty bot query opens on the whole fleet`() {
        val groups = listOf("Œil", "Gardien", "Mercury Android").map { DeliverableGroup(it, it, emptyList()) }

        assertEquals(groups, groups.matchingBots(""))
        assertEquals(groups, groups.matchingBots("   "))
    }
}
