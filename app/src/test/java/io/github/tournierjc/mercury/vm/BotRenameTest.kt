package io.github.tournierjc.mercury.vm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the rename sheet sends. The server refuses a blank name and collapses whitespace itself;
 * sending the same thing keeps one rule in one place instead of two that can drift.
 */
class BotRenameTest {

    @Test
    fun `whitespace is collapsed and the edges trimmed`() {
        assertEquals("Probe B", cleanBotName("  Probe   B  "))
        assertEquals("Gardien", cleanBotName("Gardien\n"))
    }

    @Test
    fun `fifty characters of name stay whole`() {
        val long = "a".repeat(60)
        assertEquals(long, cleanBotName(long))
    }

    @Test
    fun `beyond sixty the name is cut, not refused`() {
        assertEquals(60, cleanBotName("n".repeat(80))!!.length)
    }

    @Test
    fun `a blank name is nothing to send`() {
        assertNull(cleanBotName(""))
        assertNull(cleanBotName("    "))
        assertNull(cleanBotName("\n\t "))
    }
}
