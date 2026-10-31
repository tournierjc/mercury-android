package io.github.tournierjc.mercury.ui

import io.github.tournierjc.mercury.bots.BotMessage
import io.github.tournierjc.mercury.bots.LiveTurn
import io.github.tournierjc.mercury.data.ChatMessage
import io.github.tournierjc.mercury.data.ToolStep
import io.github.tournierjc.mercury.data.dto.BotRunDto
import io.github.tournierjc.mercury.data.dto.ReasoningBlockDto
import io.github.tournierjc.mercury.data.dto.TraceStepDto
import io.github.tournierjc.mercury.vm.BotsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the bot conversation screen draws.
 *
 * This exists because the screen used to assemble its list from a subset of the state it held: the
 * tool calls and the thinking were fetched into `BotsState.traces` / `BotsState.reasoning` and then
 * never handed to the builder, so a bot turn that ran six commands read as a bare paragraph. The
 * bug was invisible in the view model's own tests — the state was right — and no test could see the
 * `remember` block it lived in. Asserting here keeps that from happening again.
 */
class BotConversationTest {

    private val record = BotRunDto(
        runId = "340eaf77",
        botId = "ssh-ops",
        state = "completed",
        startedAt = 1_000,
        durationMs = 15_000,
        summary = "**spark** — connexion SSH OK",
    )

    private fun state(
        traces: Map<String, List<TraceStepDto>> = emptyMap(),
        reasoning: Map<String, List<ReasoningBlockDto>> = emptyMap(),
        pending: List<BotMessage> = emptyList(),
    ) = BotsUiState(
        records = listOf(record),
        prompts = mapOf(record.runId to "essaie de te connecter au spark en ssh"),
        answers = mapOf(record.runId to "**spark** — connexion SSH OK (via `fleet.py probe spark`)"),
        traces = traces,
        reasoning = reasoning,
        pending = pending,
    )

    private fun messages(
        state: BotsUiState,
        botId: String? = "ssh-ops",
        live: LiveTurn? = null,
        activity: String = "",
    ) = BotConversation.messages(
        state = state,
        botId = botId,
        live = live,
        activity = activity,
        queued = "en file",
        failed = "échec",
        noAnswer = "pas de réponse",
    )

    @Test
    fun `the tool calls the state holds reach the bubble`() {
        val traces = mapOf(
            record.runId to listOf(
                TraceStepDto(name = "run_command", arg = "cd /home/node/.mercury/bots/ssh-ops && python3 scripts/fleet.py probe spark", ok = true),
                TraceStepDto(name = "run_command", arg = "python3 scripts/fleet.py list", ok = false),
            ),
        )

        val answer = messages(state(traces = traces)).last()

        assertEquals(2, answer.steps.size)
        assertEquals("run_command", answer.steps.first().tool)
        assertTrue("the command is the step's label", answer.steps.first().label.contains("fleet.py probe spark"))
        assertTrue("a failed call stays a failure", answer.steps[1].failed)
        assertTrue("stored steps are history, drawn as a block", answer.toolTrace)
    }

    @Test
    fun `the thinking the transcript recorded reaches the bubble`() {
        val reasoning = mapOf(record.runId to listOf(ReasoningBlockDto(step = 0, text = "je vérifie que fleet.py existe")))

        val answer = messages(state(reasoning = reasoning)).last()

        assertEquals(1, answer.reasoning.size)
        assertEquals("je vérifie que fleet.py existe", answer.reasoning.single().text)
    }

    @Test
    fun `a turn with neither trace nor thinking draws exactly as before`() {
        val answer = messages(state()).last()

        assertTrue(answer.steps.isEmpty())
        assertTrue(answer.reasoning.isEmpty())
        assertEquals("**spark** — connexion SSH OK (via `fleet.py probe spark`)", answer.text)
    }

    @Test
    fun `the question is drawn above the answer being written`() {
        val echo = BotMessage(key = "job-9-me", fromBot = false, text = "ok mets en place les pipelines", at = 2_000, botId = "ssh-ops")
        val live = LiveTurn(jobId = "job-9", text = "je commence par le workflow", running = true)

        val drawn = messages(state(pending = listOf(echo)), live = live)

        assertEquals(
            listOf("essaie de te connecter au spark en ssh", "**spark** — connexion SSH OK (via `fleet.py probe spark`)", "ok mets en place les pipelines", "je commence par le workflow"),
            drawn.map { it.text },
        )
        assertEquals(ChatMessage.Role.User, drawn[2].role)
        assertEquals(ChatMessage.Role.Assistant, drawn[3].role)
        assertTrue(drawn[3].streaming)
    }

    @Test
    fun `an echo addressed to another teammate stays out of this conversation`() {
        val elsewhere = BotMessage(key = "job-9-me", fromBot = false, text = "pour Compose", at = 2_000, botId = "mac-android")

        val drawn = messages(state(pending = listOf(elsewhere)))

        assertTrue(drawn.none { it.text == "pour Compose" })
    }

    @Test
    fun `a bubble with no text of its own says which of the four silences it is`() {
        val queued = BotMessage(key = "job-7-me", fromBot = false, text = "", at = 3_000, state = "queued", botId = "ssh-ops")

        val drawn = messages(state(pending = listOf(queued)))

        assertTrue(drawn.none { it.text.isBlank() })
    }

    @Test
    fun `no bot selected draws nothing rather than the last one's thread`() {
        assertTrue(messages(state(), botId = null).isEmpty())
    }

    @Test
    fun `the step rows keep the shape the trace block draws`() {
        val traces = mapOf(record.runId to listOf(TraceStepDto(name = "read_file", arg = "/work/notes.md", ok = true)))

        val step = messages(state(traces = traces)).last().steps.single()

        assertEquals(ToolStep(tool = "read_file", label = "/work/notes.md", failed = false), step)
    }
}
