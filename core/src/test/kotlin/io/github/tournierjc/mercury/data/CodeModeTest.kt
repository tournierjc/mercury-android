package io.github.tournierjc.mercury.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The programming mode's vocabulary.
 *
 * The four values are the daemon's, and the screen must not invent a fifth: an answer it does not
 * recognise reads as `off`, which is the only safe default for a mode that can edit files.
 */
class CodeModeTest {

    @Test
    fun `the daemon's four words are understood`() {
        assertEquals(CodeMode.Off, CodeMode.from("off"))
        assertEquals(CodeMode.Auto, CodeMode.from("auto"))
        assertEquals(CodeMode.Plan, CodeMode.from("plan"))
        assertEquals(CodeMode.Execute, CodeMode.from("execute"))
    }

    @Test
    fun `case and spacing from the server do not create a new mode`() {
        assertEquals(CodeMode.Plan, CodeMode.from(" PLAN "))
        assertEquals(CodeMode.Auto, CodeMode.from("Auto"))
    }

    @Test
    fun `anything unrecognised reads as off`() {
        assertEquals(CodeMode.Off, CodeMode.from(null))
        assertEquals(CodeMode.Off, CodeMode.from(""))
        assertEquals(CodeMode.Off, CodeMode.from("turbo"))
    }

    @Test
    fun `only off is not a decision`() {
        assertFalse(CodeMode.Off.engaged)
        assertTrue(CodeMode.Auto.engaged)
        assertTrue(CodeMode.Plan.engaged)
        assertTrue(CodeMode.Execute.engaged)
    }

    @Test
    fun `the wire values are the ones the route accepts`() {
        // `POST /api/code/set` takes {state: "off"|"auto"|"plan"|"execute"}.
        assertEquals(listOf("off", "auto", "plan", "execute"), CodeMode.entries.map { it.wire })
    }
}
