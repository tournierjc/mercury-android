package io.github.tournierjc.mercury.ui.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The explorer's rules, asserted where they live.
 *
 * These are the four decisions the file tab cannot get wrong without looking broken: which crumb
 * opens which directory, that going up at the root does not walk out of the workspace, that a
 * folder is never sorted among the files, and that a size is a size rather than a byte count.
 */
class WorkspacePathTest {

    private fun entry(name: String, isDirectory: Boolean = false, size: Long = 0L) = WorkspaceEntry(
        name = name,
        path = name,
        isDirectory = isDirectory,
        size = size,
        ext = if (isDirectory) "" else ".kt",
        kind = if (isDirectory) "directory" else "kt",
    )

    @Test
    fun `a nested path reads root first with one crumb per directory`() {
        assertEquals(
            listOf(
                Crumb("/", ""),
                Crumb("mercury-agent", "mercury-agent"),
                Crumb("src", "mercury-agent/src"),
                Crumb("ui", "mercury-agent/src/ui"),
            ),
            WorkspacePath.breadcrumb("mercury-agent/src/ui"),
        )
    }

    @Test
    fun `the root is a single crumb`() {
        assertEquals(listOf(Crumb("/", "")), WorkspacePath.breadcrumb(""))
        assertEquals(listOf(Crumb("/", "")), WorkspacePath.breadcrumb("/"))
        assertEquals(listOf(Crumb("/", "")), WorkspacePath.breadcrumb("."))
    }

    @Test
    fun `going up at the root stays at the root`() {
        assertEquals("", WorkspacePath.parent(""))
        assertEquals("", WorkspacePath.parent("mercury-agent"))
        assertEquals("mercury-agent", WorkspacePath.parent("mercury-agent/src"))
        assertEquals("mercury-agent/src", WorkspacePath.parent("mercury-agent/src/ui/"))
    }

    @Test
    fun `joining a child never doubles the separator`() {
        assertEquals("README.md", WorkspacePath.join("", "README.md"))
        assertEquals("src/main.kt", WorkspacePath.join("src", "main.kt"))
        assertEquals("src/main.kt", WorkspacePath.join("/src/", "./main.kt"))
    }

    @Test
    fun `directories come first then alphabetical ignoring case`() {
        val sorted = WorkspacePath.sort(
            listOf(
                entry("zeta.kt"),
                entry("Alpha.kt"),
                entry("ui", isDirectory = true),
                entry("beta.kt"),
                entry("app", isDirectory = true),
            ),
        )
        assertEquals(
            listOf("app", "ui", "Alpha.kt", "beta.kt", "zeta.kt"),
            sorted.map { it.name },
        )
    }

    @Test
    fun `a size is a size not a byte count`() {
        assertEquals("0 B", WorkspacePath.formatSize(0))
        assertEquals("512 B", WorkspacePath.formatSize(512))
        assertEquals("1 kB", WorkspacePath.formatSize(1024))
        assertEquals("1.5 kB", WorkspacePath.formatSize(1536))
        assertEquals("5 MB", WorkspacePath.formatSize(5L * 1024 * 1024))
        assertEquals("2 GB", WorkspacePath.formatSize(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun `a preview refuses the bytes it could not decode`() {
        assertFalse(WorkspacePath.looksBinary("fun main() { println(\"ok\") }"))
        assertTrue(WorkspacePath.looksBinary("PNG\u0000\u0001\u0002"))
        assertTrue(WorkspacePath.looksBinary("\uFFFD".repeat(40) + "abc"))
    }

    @Test
    fun `a tree item with no name is not a row`() {
        assertNull(WorkspaceEntry.of(kotlinx.serialization.json.JsonObject(emptyMap())))
    }
}
