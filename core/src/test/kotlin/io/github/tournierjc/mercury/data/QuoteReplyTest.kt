package io.github.tournierjc.mercury.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The quote travels inside the message text, so what the app draws has to be exactly what it
 * sent. These cases keep the round trip honest, including the ones where a `>` is not a quote.
 */
class QuoteReplyTest {

    @Test
    fun `a quote and an answer round-trip`() {
        val sent = QuoteReply.compose("Explique le rendu incremental", "En deux phrases, voici.")
        assertEquals("> Explique le rendu incremental\n\nEn deux phrases, voici.", sent)

        val parsed = QuoteReply.parse(sent)
        assertTrue(parsed.isQuote)
        assertEquals("Explique le rendu incremental", parsed.quote)
        assertEquals("En deux phrases, voici.", parsed.body)
    }

    @Test
    fun `a multi-line quote becomes one blockquote per line`() {
        val sent = QuoteReply.compose("première ligne\nseconde ligne", "ok")
        assertEquals("> première ligne\n> seconde ligne\n\nok", sent)
        assertEquals("première ligne\nseconde ligne", QuoteReply.parse(sent).quote)
    }

    @Test
    fun `a message without a quote is left whole`() {
        val parsed = QuoteReply.parse("Bonjour, une question simple.")
        assertFalse(parsed.isQuote)
        assertEquals("Bonjour, une question simple.", parsed.body)
    }

    @Test
    fun `only the leading quote is lifted`() {
        val parsed = QuoteReply.parse("> question\n\nréponse\n> ceci est du code ou une citation")
        assertEquals("question", parsed.quote)
        assertEquals("réponse\n> ceci est du code ou une citation", parsed.body)
    }

    @Test
    fun `a quote with no answer keeps an empty body`() {
        val parsed = QuoteReply.parse("> juste la citation")
        assertEquals("juste la citation", parsed.quote)
        assertEquals("", parsed.body)
    }

    @Test
    fun `composing with an empty answer sends only the quote`() {
        assertEquals("> question", QuoteReply.compose("question", "   "))
    }

    @Test
    fun `an empty message parses to an empty body`() {
        val parsed = QuoteReply.parse("")
        assertFalse(parsed.isQuote)
        assertEquals("", parsed.body)
    }
}
