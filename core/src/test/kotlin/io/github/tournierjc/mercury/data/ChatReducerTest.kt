package io.github.tournierjc.mercury.data

import java.time.Instant
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
        var state = ChatReducer.onEvent(initial, ChatEvent.PermissionRequest("perm_1", "rm?", optionsOf("yes", "no")))
        assertNotNull(state.interaction)
        assertEquals(InteractionKind.Permission, state.interaction?.kind)
        state = ChatReducer.clearPermission(state, "perm_1")
        assertNull(state.interaction)
    }

    @Test
    fun `choice prompts are queued behind the pending permission`() {
        var state = ChatReducer.onEvent(initial, ChatEvent.PermissionRequest("perm_1", "rm?", optionsOf("yes", "no")))
        state = ChatReducer.onEvent(state, ChatEvent.ChoicePrompt("choice_1", "Quel fichier ?", optionsOf("a", "b")))

        assertEquals(2, state.interactions.size)
        assertEquals("perm_1", state.interaction?.id)
        assertEquals(optionsOf("a", "b"), state.interactions[1].options)

        state = ChatReducer.clearPermission(state, "perm_1")
        assertEquals(InteractionKind.Choice, state.interaction?.kind)
    }

    @Test
    fun `a choice without options still raises a card so the blocked turn stays visible`() {
        // DEC-9 (SPECS/115 §16, PARITE 28): the daemon holds the turn open on this prompt until its
        // own timeout. Dropping the interaction left the screen mute for the whole wait, so the
        // card is kept — buttonless, its state line drawn by the UI.
        val state = ChatReducer.onEvent(
            initial,
            ChatEvent.ChoicePrompt("choice_1", "Quel fichier ?", emptyList()),
        )

        assertNotNull(state.interaction)
        assertEquals("choice_1", state.interaction?.id)
        assertEquals(InteractionKind.Choice, state.interaction?.kind)
        assertEquals("Quel fichier ?", state.interaction?.text)
        assertEquals(emptyList<ChoiceOption>(), state.interaction?.options)
    }

    @Test
    fun `a choice whose options all lack a value keeps its card and offers no button`() {
        // Those are the two entries `presentChoicePrompt` can send when the agent wrote no usable
        // answer: `{"value":""}` has nothing to post, `{"label":"x"}` has no value at all. The
        // route is keyed on the value, so both are dropped rather than drawn as empty buttons.
        val event = MercuryEventParser.parse(
            RawSseEvent(
                type = "choice_prompt",
                data = """{"id":"choice_1","question":"Quel fichier ?","options":[{"value":""},{"label":"x"}]}""",
            ),
        )

        // Not the default branch: a parse that silently gave up would come back as Ignored and the
        // assertions below would pass on an empty state.
        assertTrue(event is ChatEvent.ChoicePrompt)
        assertEquals(emptyList<ChoiceOption>(), (event as ChatEvent.ChoicePrompt).options)

        val state = ChatReducer.onEvent(initial, event)

        assertNotNull(state.interaction)
        assertEquals(InteractionKind.Choice, state.interaction?.kind)
        assertEquals("choice_1", state.interaction?.id)
        assertEquals(emptyList<ChoiceOption>(), state.interaction?.options)
    }

    @Test
    fun `permission mode prompt is rendered as a mode interaction`() {
        val state = ChatReducer.onEvent(
            initial,
            ChatEvent.PermissionModeRequest("mode_1", optionsOf("ask-me", "allow-all")),
        )
        assertEquals(InteractionKind.Mode, state.interaction?.kind)
        assertEquals(optionsOf("ask-me", "allow-all"), state.interaction?.options)
    }

    @Test
    fun `the same interaction id is not queued twice`() {
        var state = ChatReducer.onEvent(initial, ChatEvent.PermissionRequest("perm_1", "rm?", optionsOf("yes")))
        state = ChatReducer.onEvent(state, ChatEvent.PermissionRequest("perm_1", "rm?", optionsOf("yes")))
        assertEquals(1, state.interactions.size)
    }

    @Test
    fun `loop warnings surface without stopping the turn`() {
        var state = ChatReducer.addUserMessage(initial, "salut")
        state = ChatReducer.onEvent(state, ChatEvent.TextDelta("en cours"))
        state = ChatReducer.onEvent(state, ChatEvent.Warning("boucle détectée"))

        assertEquals(NoticeKey.SERVER_WARNING, state.warning?.key)
        assertEquals("boucle détectée", state.warning?.detail)
        assertTrue(state.streaming)
        state = ChatReducer.dismissWarning(state)
        assertNull(state.warning)
    }

    @Test
    fun `provider info updates the model label`() {
        val state = ChatReducer.onEvent(initial, ChatEvent.ProviderInfo("qwen", "qwen3.8-flash-next"))
        assertEquals("qwen · qwen3.8-flash-next", state.modelLabel)
    }

    @Test
    fun `failure stops streaming and keeps the error`() {
        var state = ChatReducer.addUserMessage(initial, "salut")
        state = ChatReducer.onEvent(state, ChatEvent.TextDelta("partiel"))
        state = ChatReducer.onEvent(state, ChatEvent.Failure("réseau coupé"))

        assertFalse(state.streaming)
        assertEquals(NoticeKey.STREAM_FAILED, state.error?.key)
        assertEquals("réseau coupé", state.error?.detail)
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

    // Send failures have no voice of their own: the bubble of the owner's turn is the only place
    // that can say "this never left the phone" (SPECS/115 §9.3, the `role:"system"` form).

    @Test
    fun `a turn just written is still sending`() {
        val state = ChatReducer.addUserMessage(initial, "et le graphe ?")

        // `Sending` is not the default: a bubble that only compiles its way to `Sent` would pass a
        // test asserting the default. This one proves `addUserMessage` states the truth.
        assertEquals(ChatMessage.Delivery.Sending, state.messages.single().delivery)
        assertEquals(ChatMessage.Role.User, state.messages.single().role)
    }

    @Test
    fun `the daemon answering marks the turn sent`() {
        val opened = ChatReducer.addUserMessage(initial, "et le graphe ?")
        val id = opened.messages.single().id

        val state = ChatReducer.markUserSent(opened, id)

        assertEquals(ChatMessage.Delivery.Sent, state.messages.single().delivery)
        assertEquals("et le graphe ?", state.messages.single().text)
    }

    @Test
    fun `a turn that never reached the daemon is marked failed`() {
        val opened = ChatReducer.addUserMessage(initial, "et le graphe ?")
        val id = opened.messages.single().id

        val state = ChatReducer.markUserFailed(opened, id)

        assertEquals(ChatMessage.Delivery.Failed, state.messages.single().delivery)
        // The text the owner typed stays: the bubble must still show what did not go out.
        assertEquals("et le graphe ?", state.messages.single().text)
        // No send is in flight any more, and the reply never arrived.
        assertEquals(1, state.messages.size)
    }

    @Test
    fun `only the turn that failed carries the failure`() {
        var state = ChatReducer.addUserMessage(initial, "premier")
        state = ChatReducer.markUserSent(state, state.messages.single().id)
        state = ChatReducer.addUserMessage(state, "second")
        state = ChatReducer.markUserFailed(state, state.messages.last().id)

        assertEquals(ChatMessage.Delivery.Sent, state.messages[0].delivery)
        assertEquals(ChatMessage.Delivery.Failed, state.messages[1].delivery)
    }

    @Test
    fun `a late answer for an unknown turn paints nothing`() {
        val opened = ChatReducer.addUserMessage(initial, "et le graphe ?")

        assertEquals(opened, ChatReducer.markUserFailed(opened, "user-42"))
        // The id of a reply is never a target, even when it is the one handed in.
        val turn = finishedTurn()
        val reply = turn.messages.last()
        assertEquals(ChatMessage.Role.Assistant, reply.role)
        assertEquals(turn, ChatReducer.markUserFailed(turn, reply.id))
        // ... and the owner's live turn keeps the state it had: `Sending`, never a silent `Sent`.
        assertEquals(ChatMessage.Delivery.Sending, turn.messages.first().delivery)
    }

    @Test
    fun `a stored exchange reads back as sent`() {
        val messages = historyMessages(
            listOf(MessageDto(role = "user", content = "et le graphe ?", timestamp = 1_793_242_800_000L)),
        )

        assertEquals(ChatMessage.Delivery.Sent, messages.single().delivery)
    }

    @Test
    fun `ignored events are no-ops`() {
        val state = ChatReducer.addUserMessage(initial, "salut")
        assertEquals(state, ChatReducer.onEvent(state, ChatEvent.Ignored("status")))
    }

    // The hour under a finished reply comes back from the stored thread, not from the stream
    // (SPECS/115 §3.4): `stampAssistantAt` is the reducer's half of that.

    private val serverDate = Instant.parse("2026-10-29T03:00:42.500Z")

    private fun finishedTurn(): ChatState {
        var state = ChatReducer.addUserMessage(initial, "et le graphe ?")
        state = ChatReducer.onEvent(state, ChatEvent.TextDelta("503"))
        return ChatReducer.onEvent(state, ChatEvent.TextDone("503, je lis le motif", 900))
    }

    @Test
    fun `the finished reply takes the date the daemon stored`() {
        val state = finishedTurn()
        val reply = state.messages.last()

        val stamped = ChatReducer.stampAssistantAt(state, reply.id, serverDate)

        // The exact instant, not "not null": a fallback on the phone's clock would pass the latter.
        assertEquals(serverDate, stamped.messages.last().at)
        assertEquals("503, je lis le motif", stamped.messages.last().text)
        // The owner's bubble keeps whatever date it had — the stamp touches one message.
        assertEquals(state.messages.first().at, stamped.messages.first().at)
    }

    @Test
    fun `a reply still being written takes no date`() {
        var state = ChatReducer.addUserMessage(initial, "et le graphe ?")
        state = ChatReducer.onEvent(state, ChatEvent.TextDelta("503"))
        val reply = state.messages.last()
        assertTrue(reply.streaming)

        val stamped = ChatReducer.stampAssistantAt(state, reply.id, serverDate)

        assertNull(stamped.messages.last().at)
    }

    @Test
    fun `no date, no change`() {
        val state = finishedTurn()
        assertEquals(state, ChatReducer.stampAssistantAt(state, state.messages.last().id, null))
    }

    @Test
    fun `an unknown id leaves every bubble alone`() {
        val state = finishedTurn()
        assertEquals(state, ChatReducer.stampAssistantAt(state, "assistant-42", serverDate))
        // The owner's id is never a target, even when it is the one handed in.
        assertEquals(state, ChatReducer.stampAssistantAt(state, state.messages.first().id, serverDate))
    }

    @Test
    fun `a dated reply is never re-dated`() {
        val state = finishedTurn()
        val id = state.messages.last().id
        val once = ChatReducer.stampAssistantAt(state, id, serverDate)

        val twice = ChatReducer.stampAssistantAt(once, id, Instant.parse("2026-10-29T05:00:00Z"))

        assertEquals(serverDate, twice.messages.last().at)
    }
}
