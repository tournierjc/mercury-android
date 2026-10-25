package io.github.tournierjc.mercury.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MercuryEventParserTest {

    private fun parse(type: String, data: String) = MercuryEventParser.parse(RawSseEvent(type, data))

    @Test
    fun `decodes connected`() {
        val event = parse("connected", """{"id":"sse_1","provider":"deepseek","model":"deepseek-flash"}""")
        assertEquals(ChatEvent.Connected("deepseek", "deepseek-flash"), event)
    }

    @Test
    fun `decodes text delta`() {
        assertEquals(ChatEvent.TextDelta("Hello"), parse("text_delta", """{"text":"Hello","targetId":"s"}"""))
    }

    @Test
    fun `decodes text done with elapsed`() {
        val event = parse("text_done", """{"fullText":"done","elapsedMs":1234,"targetId":"s"}""")
        assertEquals(ChatEvent.TextDone("done", 1234L), event)
    }

    @Test
    fun `decodes tool steps`() {
        val start = parse("step_start", """{"step":1,"tool":"read_file","label":"read_file(path=a)","targetId":"s"}""")
        assertEquals(ChatEvent.StepStart("read_file", "read_file(path=a)"), start)

        val done = parse("step_done", """{"tool":"read_file","summary":"42 lines","targetId":"s"}""")
        assertEquals(ChatEvent.StepDone("read_file", "42 lines"), done)
    }

    @Test
    fun `decodes permission request`() {
        val event = parse(
            "permission_request",
            """{"id":"perm_1","prompt":"Run rm -rf build?","options":["yes","always","no"],"targetId":"s"}""",
        )
        assertEquals(
            ChatEvent.PermissionRequest("perm_1", "Run rm -rf build?", listOf("yes", "always", "no")),
            event,
        )
    }

    @Test
    fun `fails soft on truncated json`() {
        assertTrue(parse("text_delta", """{"text":"unterminated""") is ChatEvent.Ignored)
    }

    @Test
    fun `ignores unknown event types`() {
        assertEquals(ChatEvent.Ignored("bot_activity"), parse("bot_activity", """{"step":"x"}"""))
    }

    @Test
    fun `decodes session switched`() {
        val event = parse("session_switched", """{"id":"abc","title":"New chat"}""")
        assertEquals(ChatEvent.SessionSwitched("abc", "New chat"), event)
    }
}
