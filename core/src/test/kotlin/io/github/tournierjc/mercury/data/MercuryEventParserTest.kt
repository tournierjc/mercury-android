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
            ChatEvent.PermissionRequest("perm_1", "Run rm -rf build?", optionsOf("yes", "always", "no")),
            event,
        )
    }

    @Test
    fun `choice prompt keeps the server label and the value it posts`() {
        val event = parse(
            "choice_prompt",
            """{"id":"choice_1","question":"Quel fichier ?","options":[{"value":"src/main.kt","label":"src/main.kt (42 lignes)"},{"value":"build.gradle.kts","label":"build.gradle.kts"}],"targetId":"s"}""",
        )
        assertEquals(
            ChatEvent.ChoicePrompt(
                "choice_1",
                "Quel fichier ?",
                listOf(
                    ChoiceOption("src/main.kt", "src/main.kt (42 lignes)"),
                    ChoiceOption("build.gradle.kts", "build.gradle.kts"),
                ),
            ),
            event,
        )
    }

    @Test
    fun `choice prompt still reads bare strings`() {
        val event = parse(
            "choice_prompt",
            """{"id":"choice_1","question":"Quel fichier ?","options":["a","b"],"targetId":"s"}""",
        )
        assertEquals(
            ChatEvent.ChoicePrompt("choice_1", "Quel fichier ?", listOf(ChoiceOption("a"), ChoiceOption("b"))),
            event,
        )
        assertEquals(null, (event as ChatEvent.ChoicePrompt).options[0].label)
    }

    @Test
    fun `choice prompt drops options without a value`() {
        val event = parse(
            "choice_prompt",
            """{"id":"choice_1","question":"Quel fichier ?","options":[{"value":""},{"label":"x"},{"value":"a","label":"A"}],"targetId":"s"}""",
        )
        assertEquals(listOf(ChoiceOption("a", "A")), (event as ChatEvent.ChoicePrompt).options)
    }

    @Test
    fun `choice prompt never invents an option`() {
        val empty = parse("choice_prompt", """{"id":"c","question":"Quel fichier ?","options":[]}""")
        assertEquals(emptyList<ChoiceOption>(), (empty as ChatEvent.ChoicePrompt).options)

        val missing = parse("choice_prompt", """{"id":"c","question":"Quel fichier ?"}""")
        assertEquals(emptyList<ChoiceOption>(), (missing as ChatEvent.ChoicePrompt).options)
    }

    @Test
    fun `choice options are deduplicated by value and capped at eight`() {
        val duplicate = parse(
            "choice_prompt",
            """{"id":"c","question":"q","options":[{"value":"a","label":"first"},{"value":"a","label":"second"},"b"]}""",
        )
        assertEquals(
            listOf(ChoiceOption("a", "first"), ChoiceOption("b")),
            (duplicate as ChatEvent.ChoicePrompt).options,
        )

        val many = (1..12).joinToString(",") { """{"value":"v$it","label":"V$it"}""" }
        val capped = parse("choice_prompt", """{"id":"c","question":"q","options":[$many]}""")
        assertEquals(
            listOf("v1", "v2", "v3", "v4", "v5", "v6", "v7", "v8"),
            (capped as ChatEvent.ChoicePrompt).options.map { it.value },
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
