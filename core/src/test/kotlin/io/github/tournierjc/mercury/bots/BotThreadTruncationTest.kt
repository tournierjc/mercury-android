package io.github.tournierjc.mercury.bots

import io.github.tournierjc.mercury.data.dto.BotRunDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A bot's conversation has two possible sources for the answer, and they are not equivalent: the
 * transcript holds the whole thing, the journal's summary is cut at 300 characters — which lands in
 * the middle of a fenced `openui` block, and half a spec is not a layout.
 *
 * These tests pin which one is chosen, and that the screen is told when the cheap one was all there
 * was to read.
 */
class BotThreadTruncationTest {

    private fun record(runId: String, summary: String = "", error: String = "", at: Long = 1_000) =
        BotRunDto(runId = runId, summary = summary, error = error, startedAt = at, state = "done")

    @Test
    fun `the transcript wins over the summary`() {
        val messages = BotThread.build(
            records = listOf(record("r1", summary = "coupé")),
            prompts = mapOf("r1" to "fais un graphique"),
            outputs = mapOf("r1" to "```openui\n{\"root\":{}}\n```"),
        )

        val answer = messages.last()
        assertEquals("```openui\n{\"root\":{}}\n```", answer.text)
        assertFalse("a full answer is not truncated", answer.truncated)
    }

    @Test
    fun `a summary nothing else replaced is marked truncated`() {
        val messages = BotThread.build(
            records = listOf(record("r1", summary = "```openui\n{\"root\":{\"type\":\"stack\",\"chil")),
            prompts = mapOf("r1" to "fais un graphique"),
        )

        val answer = messages.last()
        assertTrue(answer.truncated)
    }

    @Test
    fun `an error line and an empty turn are not truncations`() {
        val failed = BotThread.build(listOf(record("r1", error = "crashed")), mapOf("r1" to "go")).last()
        val empty = BotThread.build(listOf(record("r2")), mapOf("r2" to "go")).last()

        assertFalse(failed.truncated)
        assertFalse(empty.truncated)
        assertEquals("crashed", failed.text)
    }

    @Test
    fun `a turn with a record is over, so its summary is shown as a summary`() {
        // The premise of this test changed: it used to treat a live tail as the answer while the
        // journal already had the run, and the run is only written when the turn is over. With a
        // record there is no stream left, so the bubble carries the journal's text and says it is a
        // summary rather than a whole answer.
        val messages = BotThread.build(
            records = listOf(record("r1", summary = "coupé")),
            prompts = mapOf("r1" to "go"),
            liveJobId = "r1",
            liveTail = "en train d'écrire…",
            liveRunning = true,
        )

        val answer = messages.last()
        assertEquals("coupé", answer.text)
        assertFalse(answer.streaming)
        assertTrue("a summary is not the whole answer", answer.truncated)
    }
}
