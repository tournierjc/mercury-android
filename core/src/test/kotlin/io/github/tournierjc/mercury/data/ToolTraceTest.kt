package io.github.tournierjc.mercury.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A reopened conversation must not show the tool summary as a paragraph.
 *
 * The daemon writes one entry per turn for the *model* to read — `- run_command … → ok` — and the
 * phone printed it verbatim. These tests pin that it is recognised, that each call becomes a step
 * with its own outcome, and that the summary's own tail ("… 12 more tool calls") is not lost.
 */
class ToolTraceTest {

    private val trace = """
        [Tool activity in my previous turn]
        - run_command command="ls -a /work" → ok (4 lines)
        - grep path="/work/src/core" → ok (30 lines)
        - read_file path="missing.kt" → ✗ file not found
    """.trimIndent()

    @Test
    fun `a stored summary is recognised by its header`() {
        assertTrue(ToolTrace.looksLike(trace))
        assertFalse(ToolTrace.looksLike("Voici la réponse, sans outils."))
        // Leading whitespace is the server's business, not a reason to miss it.
        assertTrue(ToolTrace.looksLike("\n  " + ToolTrace.HEADER))
    }

    @Test
    fun `each call becomes a step with a tool, its arguments and its outcome`() {
        val steps = ToolTrace.parse(trace)

        assertEquals(3, steps.size)
        assertEquals("run_command", steps[0].tool)
        assertEquals("command=\"ls -a /work\"", steps[0].label)
        assertEquals("ok (4 lines)", steps[0].summary)
        assertEquals("read_file", steps[2].tool)
        assertTrue("a failure keeps its mark", steps[2].summary!!.startsWith("✗"))
    }

    @Test
    fun `the header is not a step`() {
        assertFalse(ToolTrace.parse(trace).any { it.tool.contains("Tool activity") })
    }

    @Test
    fun `the truncated tail keeps its count`() {
        val steps = ToolTrace.parse(
            """
            [Tool activity in my previous turn]
            - run_command command="a" → ok
            - … 12 more tool calls
            """.trimIndent(),
        )

        assertEquals(2, steps.size)
        assertEquals("…", steps[1].tool)
        assertEquals("12 more tool calls", steps[1].label)
    }

    @Test
    fun `a line without an outcome still parses`() {
        val steps = ToolTrace.parse("[Tool activity in my previous turn]\n- read_file path=\"x\"")

        assertEquals(1, steps.size)
        assertEquals("read_file", steps[0].tool)
        assertEquals("path=\"x\"", steps[0].label)
        assertEquals(null, steps[0].summary)
    }

    @Test
    fun `an empty summary is a summary of nothing, not a step`() {
        assertEquals(emptyList<ToolStep>(), ToolTrace.parse("[Tool activity in my previous turn]"))
        assertEquals(emptyList<ToolStep>(), ToolTrace.parse(""))
    }
}
