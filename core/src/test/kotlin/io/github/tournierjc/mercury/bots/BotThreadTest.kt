package io.github.tournierjc.mercury.bots

import io.github.tournierjc.mercury.data.dto.BotRunDto
import io.github.tournierjc.mercury.data.dto.TraceStepDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun `the streaming turn shows the live tail, not the stale summary`() {
        val records = listOf(run("live", 1_000, summary = "vieux résumé"))
        val messages = BotThread.build(records, mapOf("live" to "question"), liveJobId = "live", liveTail = "en train d'écr")

        assertEquals(2, messages.size)
        assertEquals("en train d'écr", messages[1].text)
        assertTrue(messages[1].streaming)
        assertTrue(BotThread.isStreaming(messages))
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
    fun `every bubble keeps the journal's own moment`() {
        val records = listOf(run("a", at = 1_764_000_000_000, summary = "réponse", duration = 42_000))

        val messages = BotThread.build(records, mapOf("a" to "q"))

        // The question is dated at the turn's start, the answer at its end — not at "now".
        assertEquals(1_764_000_000_000L, messages[0].at ?: -1L)
        assertEquals(1_764_000_042_000L, messages[1].at ?: -1L)
    }

    @Test
    fun `an absent transcript timestamp keeps the journal's clock`() {
        // Rule (a) of SPECS/115 §3.4: every transcript written before the field has no
        // `timestamp` key at all, and those keep the hour the app already drew — the turn's own.
        val records = listOf(run("a", at = 1_764_000_000_000, summary = "réponse", duration = 42_000))

        val messages = BotThread.build(records, mapOf("a" to "q"), emptyMap(), dates = emptyMap())

        assertEquals(1_764_000_000_000L, messages[0].at ?: -1L)
        assertEquals(1_764_000_042_000L, messages[1].at ?: -1L)
    }

    @Test
    fun `an undated transcript draws no hour at all`() {
        // Rule (b): the key is there and says nothing readable, so `dates` carries the run with a
        // null value. No hour line beats an hour borrowed from the run's clock.
        val records = listOf(run("a", at = 1_764_000_000_000, summary = "réponse", duration = 42_000))

        val messages = BotThread.build(
            records,
            mapOf("a" to "q"),
            emptyMap(),
            dates = mapOf("a" to null),
        )

        assertNull(messages[0].at)
        assertNull(messages[1].at)
    }

    @Test
    fun `the transcript's timestamp dates the turn when the daemon sent one`() {
        // SPECS/115 §5 bis: the run transcript carries `timestamp`, epoch milliseconds. It is the
        // turn's own date, so it outranks the journal's enqueue time — and a run it does not
        // date keeps the journal's clock rather than losing its hour.
        val records = listOf(
            run("a", at = 1_764_000_000_000, summary = "réponse", duration = 42_000),
            run("b", at = 1_764_000_100_000, summary = "autre", duration = 5_000),
        )

        val messages = BotThread.build(
            records,
            mapOf("a" to "q", "b" to "q"),
            emptyMap(),
            dates = mapOf("a" to 1_764_000_012_500L),
        )

        assertEquals(1_764_000_012_500L, messages[0].at ?: -1L)
        assertEquals(1_764_000_054_500L, messages[1].at ?: -1L)
        // "b" had no `timestamp`: the journal's own moment, not null and not now.
        assertEquals(1_764_000_100_000L, messages[2].at ?: -1L)
    }

    @Test
    fun `a turn not yet in the journal stays undated`() {
        val messages = BotThread.build(
            emptyList(),
            emptyMap(),
            liveJobId = "pending",
            liveTail = "je commence",
            liveRunning = true,
        )

        assertEquals(1, messages.size)
        assertNull("pas d'heure inventée pour un tour non journalisé", messages[0].at)
    }

    @Test
    fun `a run with neither summary nor error still answers, empty`() {
        val messages = BotThread.build(listOf(run("a", 1_000)), mapOf("a" to "q"))
        assertEquals(2, messages.size)
        assertTrue(messages[1].fromBot)
        assertEquals("", messages[1].text)
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
        assertEquals("✗ run_command", answer.steps[1].label)
        assertEquals("gradlew :core:test", answer.steps[1].summary)
    }

    @Test
    fun `a transcript that carries no date still dates the bubble from the journal`() {
        // The daemon writes no `timestamp` field: `dates` has no key for the run, so the turn's
        // own clock dates both bubbles — and the answer is dated at the end of the turn.
        val messages = BotThread.build(
            listOf(run("a", at = 1_700_000_000_000, summary = "fait", duration = 4_000)),
            mapOf("a" to "question"),
            dates = emptyMap(),
        )

        assertEquals(1_700_000_000_000L, messages[0].at)
        assertEquals(1_700_000_004_000L, messages[1].at)
    }

    @Test
    fun `an undated transcript draws no hour, even with a journal clock`() {
        // The daemon wrote the field and said nothing usable: the app must not fall back on the
        // journal's clock (SPECS/115 §3.4 — no invented hour).
        val messages = BotThread.build(
            listOf(run("a", at = 1_700_000_000_000, summary = "fait")),
            mapOf("a" to "question"),
            dates = mapOf("a" to null),
        )

        assertNull(messages[0].at)
        assertNull(messages[1].at)
    }
}
