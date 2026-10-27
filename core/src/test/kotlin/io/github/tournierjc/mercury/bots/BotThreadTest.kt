package io.github.tournierjc.mercury.bots

import io.github.tournierjc.mercury.data.dto.BotRunDto
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
    fun `a run with neither summary nor error still answers, empty`() {
        val messages = BotThread.build(listOf(run("a", 1_000)), mapOf("a" to "q"))
        assertEquals(2, messages.size)
        assertTrue(messages[1].fromBot)
        assertEquals("", messages[1].text)
    }
}
