package io.github.tournierjc.mercury.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The daemon refuses an id that does not match `^[a-z0-9][a-z0-9-]{0,47}$`, and it refuses it
 * with an English message a French UI should never have to show. These cases are the names an
 * owner actually types.
 */
class BotSlugTest {

    @Test
    fun `accents and capitals fold`() {
        assertEquals("bricoleur", BotSlug.from("Bricoleur"))
        assertEquals("redacteur-en-chef", BotSlug.from("Rédacteur en Chef"))
    }

    @Test
    fun `spaces collapse into single dashes`() {
        assertEquals("test-bench-1", BotSlug.from("  Test   Bench 1  "))
        assertEquals("a-b", BotSlug.from("a -- b"))
    }

    @Test
    fun `punctuation goes away without leaving a dangling dash`() {
        assertEquals("veille-tech", BotSlug.from("Veille (tech) !"))
        assertEquals("ok", BotSlug.from("—ok—"))
    }

    @Test
    fun `a name that is only symbols falls back to bot`() {
        assertEquals("bot", BotSlug.from("***"))
        assertEquals("bot", BotSlug.from(""))
    }

    @Test
    fun `the id is capped at the daemon's 48 characters`() {
        val slug = BotSlug.from("x".repeat(80))
        assertEquals(48, slug.length)
        assertTrue(Regex("^[a-z0-9][a-z0-9-]{0,47}$").matches(slug))
    }

    @Test
    fun `every slug the app proposes is one the daemon accepts`() {
        val names = listOf(
            "Atelier", "Rédacteur en Chef", "Scribe 2.0", "veille/tech", "ÇA MARCHE",
            "  ", "Équipe de nuit · rendu", "🤖 robot",
        )
        val pattern = Regex("^[a-z0-9][a-z0-9-]{0,47}$")
        names.forEach { name ->
            val slug = BotSlug.from(name)
            assertTrue("\"$name\" → \"$slug\" must match the daemon's pattern", pattern.matches(slug))
        }
    }
}
