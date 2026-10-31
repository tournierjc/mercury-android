package io.github.tournierjc.mercury.bots

import io.github.tournierjc.mercury.data.dto.BotRunDto
import io.github.tournierjc.mercury.data.dto.ReasoningBlockDto
import io.github.tournierjc.mercury.data.dto.TraceStepDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bot conversation is assembled from two sources; these pin the join down. */
class BotThreadTest {

    private fun run(
        id: String,
        at: Long,
        summary: String = "",
        state: String = "completed",
        error: String = "",
        duration: Long = 1_000,
    ) = BotRunDto(
        runId = id,
        botId = "bricoleur",
        state = state,
        startedAt = at,
        durationMs = duration,
        summary = summary,
        error = error,
    )

    @Test
    fun `question then answer, oldest first`() {
        val records = listOf(
            run("b", at = 2_000, summary = "Deuxième réponse"),
            run("a", at = 1_000, summary = "Première réponse"),
        )
        val prompts = mapOf("a" to "Première question", "b" to "Deuxième question")

        val messages = BotThread.build(records, prompts)

        assertEquals(4, messages.size)
        assertFalse(messages[0].fromBot)
        assertEquals("Première question", messages[0].text)
        assertTrue(messages[1].fromBot)
        assertEquals("Première réponse", messages[1].text)
        assertEquals("Deuxième question", messages[2].text)
        assertEquals("Deuxième réponse", messages[3].text)
    }

    @Test
    fun `a run without a recorded prompt still shows its answer`() {
        val messages = BotThread.build(listOf(run("a", 1_000, summary = "fait")), emptyMap())
        assertEquals(1, messages.size)
        assertTrue(messages[0].fromBot)
        assertEquals("fait", messages[0].text)
    }

    @Test
    fun `a failed run answers with its error`() {
        val messages = BotThread.build(
            listOf(run("a", 1_000, state = "failed", error = "clé invalide")),
            mapOf("a" to "fais quelque chose"),
        )
        assertEquals("clé invalide", messages[1].text)
        assertEquals("failed", messages[1].state)
    }

    @Test
    fun `a record for the live job means the turn is over, so the tail loses`() {
        // This test used to say the opposite — « the streaming turn shows the live tail, not the
        // stale summary » — because the journal was assumed to hold a stale snapshot while a turn
        // ran. It does not: the daemon appends the record once, when the turn is over. A record for
        // the live job is therefore the end, and the 600-character tail shown over it was a finished
        // answer displayed as if it were still being written.
        val records = listOf(run("live", 1_000, summary = "la réponse du journal"))
        val messages = BotThread.build(records, mapOf("live" to "question"), liveJobId = "live", liveTail = "en train d'écr")

        assertEquals(2, messages.size)
        assertEquals("la réponse du journal", messages[1].text)
        assertFalse(messages[1].streaming)
        assertFalse(BotThread.isStreaming(messages))
    }

    @Test
    fun `a tail for a job the journal does not know yet is appended once`() {
        val messages = BotThread.build(emptyList(), emptyMap(), liveJobId = "fresh", liveTail = "début")
        assertEquals(1, messages.size)
        assertTrue(messages[0].fromBot)
        assertTrue(messages[0].streaming)
        assertEquals("début", messages[0].text)
    }

    @Test
    fun `a finished turn keeps its answer, even when the transcript has not arrived`() {
        // The shape of the reported bug: the owner watched the answer being written, the turn
        // ended, the transcript fetch lost the race and the bubble fell back to a 300-character
        // summary. The daemon now keeps the whole answer in the journal, so the fallback is
        // complete — this pins the client side of that contract.
        val full = "Livré : fiche.md.\n\n" + "Une phrase de l'explication. ".repeat(40) + "Fin."
        val records = listOf(run("done", 1_000, summary = full))
        val messages = BotThread.build(
            records,
            mapOf("done" to "que sais-tu de ce Spark ?"),
            outputs = emptyMap(),
            liveJobId = "done",
            liveTail = "Fin.",
            liveRunning = false,
        )

        assertEquals(full, messages[1].text)
        assertFalse("un tour fini ne clignote plus", messages[1].streaming)
    }

    @Test
    fun `no streaming tail means no duplicate bubble`() {
        val records = listOf(run("a", 1_000, summary = "réponse"))
        val messages = BotThread.build(records, mapOf("a" to "q"), liveJobId = "a", liveTail = "")
        assertEquals(2, messages.size)
        assertEquals("réponse", messages[1].text)
        assertFalse(BotThread.isStreaming(messages))
    }

    @Test
    fun `a run with neither summary nor error still answers, empty`() {
        val messages = BotThread.build(listOf(run("a", 1_000)), mapOf("a" to "q"))
        assertEquals(2, messages.size)
        assertTrue(messages[1].fromBot)
        assertEquals("", messages[1].text)
    }

    @Test
    fun `a turn the journal has written is never shown as still streaming`() {
        // The daemon appends a run's record when the turn ends. A missed `turn-end` used to leave
        // the 600-character tail on screen with a cursor, over a finished turn whose full answer
        // was already in the transcript.
        val records = listOf(run("a", at = 1_000, summary = "Résumé court", state = "completed"))

        val messages = BotThread.build(
            records,
            mapOf("a" to "La question"),
            mapOf("a" to "La réponse complète, en entier."),
            liveJobId = "a",
            liveTail = "…fin de la réponse",
            liveRunning = true,
        )

        val answer = messages.last()
        assertTrue(answer.fromBot)
        assertEquals("La réponse complète, en entier.", answer.text)
        assertFalse(answer.streaming)
    }

    @Test
    fun `a job the journal has not written yet still streams its tail`() {
        val messages = BotThread.build(
            emptyList(),
            emptyMap(),
            liveJobId = "z",
            liveTail = "Je commence par lire le dépôt",
            liveRunning = true,
        )

        assertEquals(1, messages.size)
        assertEquals("Je commence par lire le dépôt", messages[0].text)
        assertTrue(messages[0].streaming)
    }

    @Test
    fun `a tail left over after the turn ends is a memory, not a stream`() {
        val messages = BotThread.build(
            emptyList(),
            emptyMap(),
            liveJobId = "z",
            liveTail = "Je commence par lire le dépôt",
            liveRunning = false,
        )

        assertEquals(1, messages.size)
        assertFalse(messages[0].streaming)
    }

    @Test
    fun `the turn's tool calls ride the answer bubble, in order`() {
        val trace = listOf(
            TraceStepDto(name = "read_file", ok = true, arg = "src/a.kt"),
            TraceStepDto(name = "run_command", ok = false, arg = "gradlew :core:test"),
            TraceStepDto(name = "bot_deliver", ok = true, arg = "rapport.md"),
        )
        val messages = BotThread.build(
            listOf(run("a", 1_000, summary = "fait")),
            mapOf("a" to "question"),
            traces = mapOf("a" to trace),
        )

        val answer = messages.last { it.fromBot }
        assertEquals(3, answer.steps.size)
        assertEquals(listOf("read_file", "run_command", "bot_deliver"), answer.steps.map { it.tool })
        // The argument, not the tool's name again: the trace prints the name from `tool`, so a
        // label holding it too read as "run_command run_command" on screen.
        assertEquals("gradlew :core:test", answer.steps[1].label)
        assertEquals(null, answer.steps[1].summary)
        // A failed call keeps its place and says so with a flag, not with a `✗` glued to the label.
        assertTrue(answer.steps[1].failed)
        assertFalse(answer.steps[0].failed)
    }

    @Test
    fun `a journal line the thread has not seen makes the conversation stale`() {
        // The screen reloads on this signal, so it has to fire exactly when there is something new —
        // and it is the only path left when the live feed never reported the turn's end.
        assertTrue(BotThread.isStale(knownRunId = "run-1", newestRunId = "run-2"))
        assertTrue(BotThread.isStale(knownRunId = null, newestRunId = "run-1"))
    }

    @Test
    fun `the same run, or no journal at all, is not stale`() {
        assertFalse(BotThread.isStale(knownRunId = "run-1", newestRunId = "run-1"))
        assertFalse(BotThread.isStale(knownRunId = "run-1", newestRunId = null))
        assertFalse(BotThread.isStale(knownRunId = "run-1", newestRunId = "  "))
    }

    // The conversation showed the calls and the reply — the *what*. A turn that read six files and
    // answered "rien à signaler" left no way to tell a check from a guess.
    @Test
    fun `the thinking lands on its own turn, never the next one`() {
        val records = listOf(
            BotRunDto(runId = "r1", startedAt = 1_000, durationMs = 10, state = "completed", summary = "premier"),
            BotRunDto(runId = "r2", startedAt = 2_000, durationMs = 10, state = "completed", summary = "second"),
        )

        val messages = BotThread.build(
            records,
            prompts = emptyMap(),
            outputs = mapOf("r1" to "premier", "r2" to "second"),
            reasoning = mapOf("r1" to listOf(ReasoningBlockDto(step = 1, text = "je vérifie X"))),
            reasoningTruncated = setOf("r1"),
        )

        val bots = messages.filter { it.fromBot }
        assertEquals(listOf("je vérifie X"), bots[0].reasoning.map { it.text })
        assertTrue(bots[0].reasoningTruncated)
        assertTrue(bots[1].reasoning.isEmpty())
        assertFalse(bots[1].reasoningTruncated)
    }

    @Test
    fun `a daemon that sends no thinking leaves the message unchanged`() {
        val records = listOf(BotRunDto(runId = "r1", startedAt = 1_000, durationMs = 10, state = "completed", summary = "ok"))

        val messages = BotThread.build(records, prompts = emptyMap())
        val bot = messages.single { it.fromBot }

        assertTrue(bot.reasoning.isEmpty())
        assertFalse(bot.reasoningTruncated)
    }

    @Test
    fun `the owner's message survives a refresh and leaves when the journal has its run`() {
        val mine = BotMessage(key = "job-42-me", fromBot = false, text = "relance les tests", at = 1_000)

        // No record yet: the daemon accepted the turn, the journal is silent — the echo stays.
        assertEquals(listOf("job-42-me"), BotThread.keepPending(listOf(mine), emptyList(), now = 2_000).map { it.key })

        // A record for another run says nothing about this one.
        val other = run(id = "job-7", at = 1_500)
        assertEquals(1, BotThread.keepPending(listOf(mine), listOf(other), now = 2_000).size)

        // Its own record means the real message is in the thread now: the echo must go.
        val landed = run(id = "job-42", at = 2_000)
        assertTrue(BotThread.keepPending(listOf(mine), listOf(landed), now = 2_000).isEmpty())
    }

    @Test
    fun `a turn that has written nothing shows the phase it is in`() {
        val built = BotThread.build(
            records = emptyList(),
            prompts = emptyMap(),
            liveJobId = "job-9",
            liveTail = "",
            liveRunning = true,
            liveActivity = "running command",
        )

        assertEquals(1, built.size)
        assertEquals("activity", built.single().state)
        assertEquals("running command", built.single().activity)
        assertTrue(built.single().streaming)
        assertEquals("", built.single().text)
    }

    @Test
    fun `no phase and no text leaves the thread empty rather than inventing a bubble`() {
        val built = BotThread.build(emptyList(), emptyMap(), liveJobId = "job-9", liveTail = "", liveRunning = true, liveActivity = "")

        assertTrue(built.isEmpty())
    }

    @Test
    fun `an echo whose turn never lands is given up on rather than kept forever`() {
        val mine = BotMessage(key = "job-99-me", fromBot = false, text = "tu es là ?", at = 1_000)

        val threeHoursLater = BotThread.keepPending(listOf(mine), emptyList(), now = 1_000 + 3 * 60 * 60 * 1000L)

        assertTrue(threeHoursLater.isEmpty())
    }
}
