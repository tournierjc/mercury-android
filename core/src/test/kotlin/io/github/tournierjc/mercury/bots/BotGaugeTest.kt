package io.github.tournierjc.mercury.bots

import io.github.tournierjc.mercury.data.dto.BotRunDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bot conversation's gauge, read from the journal.
 *
 * A bot's turns run outside the session store: there is no keeper window here, and the one number
 * a gauge can honestly show is what the thread has cost so far. The journal carries it per run;
 * [BotGauge] joins it — and must not let a slash answer (journaled with no tokens) inflate the cost.
 */
class BotGaugeTest {

    private fun run(
        runId: String,
        tokensIn: Long = 0,
        tokensOut: Long = 0,
    ) = BotRunDto(runId = runId, tokensIn = tokensIn, tokensOut = tokensOut)

    @Test
    fun `the thread's cost is the sum of what its turns read and wrote`() {
        val view = BotGauge.view(
            listOf(
                run("r1", tokensIn = 1_200, tokensOut = 300),
                run("r2", tokensIn = 2_000, tokensOut = 800),
            ),
        )

        assertEquals(4_300L, view.tokens)
        assertEquals(2, view.turns)
    }

    @Test
    fun `a slash answer counts as a turn but adds no cost`() {
        // The daemon journals a daemon-answered command with no tokens (recordDirectAnswer):
        // it is part of the conversation, not part of what the model read.
        val view = BotGauge.view(
            listOf(
                run("r1", tokensIn = 1_000, tokensOut = 200),
                run("slash-abc"),
            ),
        )

        assertEquals(1_200L, view.tokens)
        assertEquals(2, view.turns)
    }

    @Test
    fun `an empty thread hides the bar`() {
        assertTrue(BotGauge.view(emptyList()).isEmpty)
        assertTrue(BotGauge.view(listOf(run("r1"))).isEmpty)
    }

    @Test
    fun `a thread with real turns is never empty, whatever the tokens say`() {
        val view = BotGauge.view(listOf(run("r1", tokensOut = 5)))

        assertFalse(view.isEmpty)
        assertEquals(5L, view.tokens)
    }
}
