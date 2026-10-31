package io.github.tournierjc.mercury.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The trace's vocabulary: one emoji per kind of call, and the command the call ran as the detail.
 *
 * Every case here is a shape the daemon really sends — a live step's `tool(args)` label, a run
 * transcript's argument with no summary, a stored summary line, a failed call.
 */
class ToolGlyphTest {

    @Test
    fun `a shell command, a read, a write and a skill each get their own emoji`() {
        assertEquals("💻", ToolGlyph.of("run_command"))
        assertEquals("💻", ToolGlyph.of("cd"))
        assertEquals("📄", ToolGlyph.of("read_file"))
        assertEquals("📝", ToolGlyph.of("create_file"))
        assertEquals("✏️", ToolGlyph.of("edit_file"))
        assertEquals("📁", ToolGlyph.of("list_dir"))
        assertEquals("🌿", ToolGlyph.of("git_status"))
        assertEquals("🔎", ToolGlyph.of("web_search"))
        assertEquals("🌐", ToolGlyph.of("fetch_url"))
        assertEquals("📚", ToolGlyph.of("use_skill"))
        assertEquals("📦", ToolGlyph.of("bot_deliver"))
        assertEquals("💬", ToolGlyph.of("bot_send"))
        assertEquals("👥", ToolGlyph.of("fleet_delegate"))
    }

    @Test
    fun `a tool nobody mapped still gets an icon`() {
        // A plugin's tool name cannot be known in advance; an empty column would read as a bug.
        assertEquals("🔧", ToolGlyph.of("some_plugin_tool"))
        assertEquals("🔧", ToolGlyph.of(""))
    }

    @Test
    fun `a live step's label becomes the command, not the tool's name twice`() {
        val line = ToolGlyph.lineOf(
            ToolStep(tool = "run_command", label = """run_command(command=ls -a /work)"""),
        )

        assertEquals("💻", line.glyph)
        assertEquals("run_command", line.name)
        assertEquals("ls -a /work", line.detail)
        assertEquals(null, line.outcome)
        assertFalse(line.failed)
    }

    @Test
    fun `a command that is not the first argument keeps its key`() {
        // Only `command=` is redundant in front of a code block: `path=` says which argument it is.
        val line = ToolGlyph.lineOf(ToolStep(tool = "read_file", label = """read_file(path=src/a.kt)"""))
        assertEquals("""path=src/a.kt""", line.detail)
    }

    @Test
    fun `a transcript step carries its argument as the detail`() {
        // BotThread builds these: the argument is in the label, and there is no summary to show.
        val line = ToolGlyph.lineOf(ToolStep(tool = "run_command", label = "ls /work"))
        assertEquals("run_command", line.name)
        assertEquals("ls /work", line.detail)
    }

    @Test
    fun `a label that repeats the tool's name is not drawn again`() {
        val line = ToolGlyph.lineOf(ToolStep(tool = "run_command", label = "run_command"))
        assertEquals("", line.detail)
    }

    @Test
    fun `a failed call is a flag, and its outcome keeps the mark in the data`() {
        val line = ToolGlyph.lineOf(
            ToolStep(tool = "read_file", label = "missing.kt", summary = "✗ file not found"),
        )
        assertTrue(line.failed)
        assertEquals("✗ file not found", line.outcome)
    }

    @Test
    fun `a failed transcript step is red even without a mark in the label`() {
        val line = ToolGlyph.lineOf(ToolStep(tool = "run_command", label = "find /work", failed = true))
        assertTrue(line.failed)
        assertEquals("find /work", line.detail)
    }

    @Test
    fun `a stored summary line reads as the same row as a live one`() {
        // ToolTrace parses the journal's `- tool args → outcome` block; the trace then draws it
        // exactly like a step that was watched live.
        val steps = ToolTrace.parse(
            """
            [Tool activity in my previous turn]
            - run_command command="ls -a /work" → ok (4 lines)
            - read_file path="x" → ✗ file not found
            """.trimIndent(),
        )

        val first = ToolGlyph.lineOf(steps[0])
        assertEquals("💻", first.glyph)
        assertEquals("run_command", first.name)
        // `command=` in front of a code block names nothing the block does not already say.
        assertEquals("ls -a /work", first.detail)
        assertFalse(first.failed)

        val second = ToolGlyph.lineOf(steps[1])
        assertEquals("📄", second.glyph)
        assertTrue(second.failed)
    }

    @Test
    fun `the ellipsis tail of a summary keeps its own glyph`() {
        val steps = ToolTrace.parse(
            """
            [Tool activity in my previous turn]
            - … 12 more tool calls
            """.trimIndent(),
        )
        assertEquals("⋯", ToolGlyph.lineOf(steps.single()).glyph)
    }
}
