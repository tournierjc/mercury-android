package io.github.tournierjc.mercury.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The composer's command palette: what a query matches, and what a pick writes into the field.
 *
 * The catalogue is the web app's palette, so the tests here are about behaviour rather than content:
 * a typed word finds commands by name, a pick never leaves two commands in the field, and the
 * command is followed by a space because most of these take an argument.
 */
class SlashCommandsTest {

    @Test
    fun `a blank query offers the whole catalogue, in its order`() {
        assertEquals(SlashCommands.all, SlashCommands.matching(""))
        assertEquals(SlashCommands.all, SlashCommands.matching("   "))
    }

    @Test
    fun `a query matches command names, with or without the slash`() {
        val bySlash = SlashCommands.matching("/bg").map { it.command }
        val byWord = SlashCommands.matching("bg").map { it.command }

        assertEquals(bySlash, byWord)
        assertTrue("everything about /bg", bySlash.containsAll(listOf("/bg", "/bg list", "/bg killall")))
        // Case is the owner's business.
        assertEquals(SlashCommands.matching("BG"), SlashCommands.matching("bg"))
    }

    @Test
    fun `a query matches the command and not its description`() {
        // "tâche" is in the French descriptions of /bg stop and /bg list; nobody typing a command
        // wants a word search through prose.
        assertEquals(emptyList<SlashCommand>(), SlashCommands.matching("tâche"))
    }

    @Test
    fun `a pick writes the command into an empty field, ready for its argument`() {
        assertEquals("/help ", SlashCommands.insert("", "/help"))
        assertEquals("/models use ", SlashCommands.insert("", "/models use"))
    }

    @Test
    fun `a pick replaces the command being typed instead of appending it`() {
        assertEquals("/bg list ", SlashCommands.insert("/bg", "/bg list"))
        assertEquals("/help ", SlashCommands.insert("/he", "/help"))
    }

    @Test
    fun `a pick after plain text is added after it`() {
        assertEquals("note to self /help ", SlashCommands.insert("note to self", "/help"))
        assertEquals("run this /bg killall ", SlashCommands.insert("run this /bg", "/bg killall"))
    }

    @Test
    fun `the catalogue is the daemon's vocabulary`() {
        val commands = SlashCommands.all.map { it.command }.toSet()

        // The ones the owner reaches for, and the argument-taking forms that must not be missing.
        assertTrue(
            commands.containsAll(
                listOf("/help", "/progress", "/halt", "/stop", "/agents", "/bg list", "/models use", "/code plan", "/budget set"),
            ),
        )
        // Every command starts with a slash and has no trailing space: it is typed, not prose.
        assertTrue(SlashCommands.all.all { it.command.startsWith("/") && it.command.trim() == it.command })
    }
}
