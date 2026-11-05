package io.github.tournierjc.mercury.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The context-compression event the daemon writes when its keeper cuts.
 *
 * The event is not a tool call but speaks the trace's grammar — one
 * `- context_compression … → …` line under a `[Context compression]` header — so the phone draws it
 * in the same framed block as the calls, with its own glyph and without the "outils" count claiming
 * a tool ran. These pin the recognition, the parse, and the glyph.
 */
class ContextCompressionEventTest {

    private val event = """
        [Context compression]
        - context_compression 6 turns moved out → 9500 → 4100 tokens (6 archived, archived through turn 13)
    """.trimIndent()

    @Test
    fun `the compression event is recognised as a trace and as a compression`() {
        assertTrue(ToolTrace.looksLike(event))
        assertTrue(ToolTrace.looksLikeCompression(event))
        // An ordinary trace is a trace but not a compression; prose is neither.
        assertTrue(ToolTrace.looksLike("${ToolTrace.HEADER}\n- run_command ls → ok"))
        assertFalse(ToolTrace.looksLikeCompression("${ToolTrace.HEADER}\n- run_command ls → ok"))
        assertFalse(ToolTrace.looksLikeCompression("Le contexte vient d'être compressé."))
        assertFalse(ToolTrace.looksLike("Voici la réponse, sans outils."))
    }

    @Test
    fun `the event parses to one step the block can draw`() {
        val steps = ToolTrace.parse(event)

        assertEquals(1, steps.size)
        assertEquals(ToolTrace.COMPRESSION_TOOL, steps[0].tool)
        assertEquals("6 turns moved out", steps[0].label)
        assertEquals("9500 → 4100 tokens (6 archived, archived through turn 13)", steps[0].summary)
        assertFalse(steps[0].failed)
    }

    @Test
    fun `the compression gets its own glyph, never the shell one`() {
        val line = ToolGlyph.lineOf(ToolTrace.parse(event)[0])

        assertEquals("🗜️", line.glyph)
        assertEquals("context_compression", line.name)
        assertEquals("6 turns moved out", line.detail)
        assertFalse(line.failed)
        // A run_command next to it keeps its own picture.
        assertEquals("💻", ToolGlyph.of("run_command"))
    }
}
