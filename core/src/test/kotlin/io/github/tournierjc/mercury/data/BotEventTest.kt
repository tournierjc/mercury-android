package io.github.tournierjc.mercury.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BotEventTest {

    @Test
    fun `roster carries bots and queue counts`() {
        val event = BotEvent.parse(
            "bot_roster",
            """{"bots":[{"id":"a","name":"A","needsYou":true}],"queue":{"pending":3,"claimed":1,"dlq":2}}""",
        ) as BotEvent.Roster

        assertEquals(1, event.bots.size)
        assertEquals(3, event.queue.pending)
        assertEquals(1, event.queue.claimed)
        assertEquals(2, event.queue.dlq)
    }

    @Test
    fun `activity carries the passive label shown in the roster`() {
        val event = BotEvent.parse(
            "bot_activity",
            """{"botId":"a","jobId":"j1","kind":"tool","label":"Reading /work/report.md","detail":"/work/report.md","status":"running"}""",
        ) as BotEvent.Activity

        assertEquals("a", event.botId)
        assertEquals("tool", event.kind)
        assertEquals("Reading /work/report.md", event.label)
        assertEquals("running", event.status)
    }

    @Test
    fun `thinking events carry the reasoning tail captured from the live stream`() {
        val event = BotEvent.parse(
            "bot_activity",
            """{"botId":"api-probe","jobId":"8e125d34","elapsedMs":2678,"kind":"thinking","label":"thinking","stepIndex":2,"reasoningTail":"The instruction says reply OK.","textTail":"Nothing to do."}""",
        ) as BotEvent.Activity

        assertEquals("thinking", event.kind)
        assertEquals(2, event.stepIndex)
        assertEquals(2678L, event.elapsedMs)
        assertEquals("The instruction says reply OK.", event.reasoningTail)
        assertEquals("Nothing to do.", event.textTail)
    }

    @Test
    fun `turn-end reports completion`() {
        val event = BotEvent.parse(
            "bot_activity",
            """{"botId":"api-probe","jobId":"8e125d34","elapsedMs":2679,"kind":"turn-end","label":"completed — no deliverable","stepIndex":2,"status":"done"}""",
        ) as BotEvent.Activity

        assertEquals("turn-end", event.kind)
        assertEquals("done", event.status)
        assertEquals("completed — no deliverable", event.label)
    }

    @Test
    fun `notice is a plain message`() {
        val event = BotEvent.parse("bot_notice", """{"message":"Fleet ready — 3 crew members created"}""") as BotEvent.Notice
        assertTrue(event.message.startsWith("Fleet ready"))
    }

    @Test
    fun `unknown types and broken payloads never throw`() {
        assertEquals(BotEvent.Unknown("chatty"), BotEvent.parse("chatty", """{"x":1}"""))
        assertEquals(BotEvent.Unknown("bot_roster"), BotEvent.parse("bot_roster", "not json"))
        assertEquals(BotEvent.Unknown(""), BotEvent.parse(null, "{}"))
    }

    @Test
    fun `roster tolerates a missing queue`() {
        val event = BotEvent.parse("bot_roster", """{"bots":[]}""") as BotEvent.Roster
        assertEquals(0, event.queue.pending)
        assertTrue(event.bots.isEmpty())
    }
}
