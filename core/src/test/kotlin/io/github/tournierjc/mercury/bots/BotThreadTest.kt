package io.github.tournierjc.mercury.bots

import io.github.tournierjc.mercury.data.dto.BotRunDto
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
        assertEquals("\u2717 run_command", answer.steps[1].label)
        assertEquals("gradlew :core:test", answer.steps[1].summary)
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
}
