package io.github.tournierjc.mercury.diff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The phone's half of the `diff-view` contract: the same fence, the same classification.
 * Each case here mirrors a branch of the plugin's `renderer/diff.html`, so the same patch
 * cannot be coloured differently on the two clients.
 */
class DiffTest {

    private val patch = listOf(
        "diff --git a/src/app.ts b/src/app.ts",
        "index 3f1a2b7..9c4d5e6 100644",
        "--- a/src/app.ts",
        "+++ b/src/app.ts",
        "@@ -12,7 +12,8 @@ export function start() {",
        "   const app = createApp();",
        "-  app.listen(3000);",
        "+  app.listen(process.env.PORT ?? 3000);",
        "+  log.info('listening');",
        "   return app;",
    ).joinToString("\n")

    @Test
    fun `the six line kinds are the plugin's six`() {
        val kinds = Diff.parse(patch).lines.map { it.kind }
        assertEquals(
            listOf(
                DiffLine.Kind.META,
                DiffLine.Kind.META,
                DiffLine.Kind.META,
                DiffLine.Kind.META,
                DiffLine.Kind.HUNK,
                DiffLine.Kind.CONTEXT,
                DiffLine.Kind.DEL,
                DiffLine.Kind.ADD,
                DiffLine.Kind.ADD,
                DiffLine.Kind.CONTEXT,
            ),
            kinds,
        )
    }

    @Test
    fun `file headers are meta, never an added and a removed line`() {
        // `+++` starts with '+' and `---` with '-': classifying before stripping the sign is
        // the whole point of the order in the plugin's renderer.
        val document = Diff.parse(patch)
        assertEquals(2, document.added)
        assertEquals(1, document.removed)
        assertEquals("--- a/src/app.ts", document.lines[2].text)
        assertEquals("+++ b/src/app.ts", document.lines[3].text)
    }

    @Test
    fun `the sign is dropped from added and removed lines, kept everywhere else`() {
        val document = Diff.parse(patch)
        assertEquals("  app.listen(process.env.PORT ?? 3000);", document.lines[7].text)
        assertEquals("  app.listen(3000);", document.lines[6].text)
        assertEquals("   return app;", document.lines[9].text)
        assertEquals("@@ -12,7 +12,8 @@ export function start() {", document.lines[4].text)
    }

    @Test
    fun `a trailing newline does not add an empty line`() {
        assertEquals(10, Diff.parse(patch).lines.size)
        assertEquals(10, Diff.parse("$patch\n").lines.size)
        assertEquals(10, Diff.parse("$patch\n\n").lines.size)
    }

    @Test
    fun `crlf patches are read like lf ones`() {
        val crlf = patch.replace("\n", "\r\n")
        val lf = Diff.parse(patch)
        val parsed = Diff.parse(crlf)
        assertEquals(lf.added, parsed.added)
        assertEquals(lf.removed, parsed.removed)
        assertTrue(parsed.lines.none { it.text.endsWith("\r") })
    }

    @Test
    fun `an empty fence draws nothing rather than failing`() {
        val empty = Diff.parse("")
        assertEquals(0, empty.lines.size)
        assertEquals(0, empty.added)
        assertEquals(0, empty.removed)
        assertEquals(0, Diff.parse("\n\n").lines.size)
    }

    @Test
    fun `a half-written patch is drawn as written`() {
        // The renderer runs mid-stream: no parse to fail, so the counts simply reflect what
        // has arrived so far.
        val partial = Diff.parse("--- a/x\n+++ b/x\n@@ -1 +1 @@\n-old\n+new")
        assertEquals(1, partial.added)
        assertEquals(1, partial.removed)
        assertEquals("new", partial.lines.last().text)
    }

    @Test
    fun `the fence name is the one the plugin claims`() {
        assertEquals("diff", Diff.FENCE)
    }
}
