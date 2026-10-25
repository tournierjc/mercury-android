package io.github.tournierjc.mercury.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatReducerTest {

    private val initial = ChatState()

    @Test
    fun `deltas accumulate in one assistant message`() {
        var state = ChatReducer.addUserMessage(initial, "salut")
        state = ChatReducer.onEvent(state, ChatEvent.TextDelta("Bon"))
        state = ChatReducer.onEvent(state, ChatEvent.TextDelta("jour"))

        assertEquals(2, state.messages.size)
        assertEquals("Bonjour", state.messages.last().text)
        assertTrue(state.messages.last().streaming)
        assertTrue(state.streaming)
    }

    @Test
    fun `text done is authoritative and ends streaming`() {
        var state = ChatReducer.addUserMessage(initial, "salut")
        state = ChatReducer.onEvent(state, ChatEvent.TextDelta("Bonj"))
        state = ChatReducer.onEvent(state, ChatEvent.TextDone("Bonjour !", 900))

        assertEquals("Bonjour !", state.messages.last().text)
        assertFalse(state.messages.last().streaming)
        assertFalse(state.streaming)
    }

    @Test
    fun `thinking marks the turn as running without adding a message`() {
        var state = ChatReducer.addUserMessage(initial, "salut")
        state = ChatReducer.onEvent(state, ChatEvent.Thinking)

        assertEquals(1, state.messages.size)
        assertTrue(state.streaming)
    }

    @Test
    fun `tool steps are attached and completed in place`() {
        var state = ChatReducer.onEvent(initial, ChatEvent.StepStart("read_file", "read_file(path=a)"))
        state = ChatReducer.onEvent(state, ChatEvent.StepDone("read_file", "42 lines"))

        val steps = state.messages.last().steps
        assertEquals(1, steps.size)
        assertEquals("read_file(path=a)", steps[0].label)
        assertEquals("42 lines", steps[0].summary)
    }

    @Test
    fun `permission request is exposed and cleared`() {
        var state = ChatReducer.onEvent(initial, ChatEvent.PermissionRequest("perm_1", "rm?", listOf("yes", "no")))
        assertNotNull(state.pendingPermission)
        state = ChatReducer.clearPermission(state)
        assertNull(state.pendingPermission)
    }

    @Test
    fun `failure stops streaming and keeps the error`() {
        var state = ChatReducer.addUserMessage(initial, "salut")
        state = ChatReducer.onEvent(state, ChatEvent.TextDelta("partiel"))
        state = ChatReducer.onEvent(state, ChatEvent.Failure("réseau coupé"))

        assertFalse(state.streaming)
        assertEquals("réseau coupé", state.error)
        assertFalse(state.messages.last().streaming)
    }

    @Test
    fun `session switched rebinds the session id`() {
        val state = ChatReducer.onEvent(initial, ChatEvent.SessionSwitched("sess-9", "Ma session"))
        assertEquals("sess-9", state.sessionId)
        assertEquals("Ma session", state.title)
    }

    @Test
    fun `new chat wipes the transcript but keeps the connection`() {
        var state = ChatReducer.addUserMessage(initial, "salut")
        state = ChatReducer.onEvent(state, ChatEvent.SessionSwitched("sess-1", "x"))
        state = ChatReducer.newChat(state)

        assertTrue(state.messages.isEmpty())
        assertNull(state.sessionId)
        assertFalse(state.streaming)
    }

    @Test
    fun `ignored events are no-ops`() {
        val state = ChatReducer.addUserMessage(initial, "salut")
        assertEquals(state, ChatReducer.onEvent(state, ChatEvent.Ignored("status")))
    }
}
