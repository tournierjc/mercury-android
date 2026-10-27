package io.github.tournierjc.mercury.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The terminal screen resolves `cd` itself, so this arithmetic is the whole of that behaviour.
 *
 * Paths are workspace-relative: "" is the workspace root, and the exec route takes the same shape
 * as `cwd`. Anything that escapes the workspace (`cd ../../..`) collapses to the root rather than
 * producing a path the daemon would reject.
 */
class TerminalPathsTest {

    @Test
    fun `cd into a sibling directory`() {
        assertEquals("src/main", TerminalPaths.resolve("", "src/main"))
        assertEquals("repo/src", TerminalPaths.resolve("repo", "src"))
    }

    @Test
    fun `cd dotdot walks back up`() {
        assertEquals("src", TerminalPaths.resolve("src/main", ".."))
        assertEquals("", TerminalPaths.resolve("src", ".."))
        // Above the root stays at the root.
        assertEquals("", TerminalPaths.resolve("", "../.."))
    }

    @Test
    fun `cd with no argument and cd ~ both go home`() {
        assertEquals("", TerminalPaths.resolve("a/b", ""))
        assertEquals("", TerminalPaths.resolve("a/b", "~"))
    }

    @Test
    fun `cd - returns to the parent`() {
        assertEquals("a", TerminalPaths.resolve("a/b", "-"))
        assertEquals("", TerminalPaths.resolve("a", "-"))
    }

    @Test
    fun `an absolute target is read as workspace-relative`() {
        // The daemon's cwd is relative to the workspace; a leading slash would be refused.
        assertEquals("etc/hosts", TerminalPaths.resolve("a", "/etc/hosts"))
    }

    @Test
    fun `dots and doubled slashes collapse`() {
        assertEquals("a/b", TerminalPaths.normalise("./a//b/./"))
        assertEquals("b", TerminalPaths.normalise("a/../b"))
    }

    @Test
    fun `the prompt shows a tilde at the root`() {
        assertEquals("mercury~", TerminalPaths.prompt(""))
        assertEquals("mercury/repo/src", TerminalPaths.prompt("repo/src"))
    }
}
