package io.github.tournierjc.mercury.data

import io.github.tournierjc.mercury.util.formatCost
import io.github.tournierjc.mercury.util.formatTokens
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a board costs, and what its four counters say.
 *
 * The phone showed a card's *budget* and never what it had spent, and its lanes counted nothing at
 * all: the numbers the web app prints above a board had no equivalent on the device. These tests pin
 * the parsing (two shapes of token usage), the estimate the web app uses, and the counters — counted
 * from the cards rather than trusted from a summary that could disagree with the stream.
 */
class BoardSpendTest {

    private fun card(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    @Test
    fun `a card that ran reports both directions, and its total`() {
        val spend = BoardSpend.of(card("""{"status":"done","tokenUsage":{"input":120000,"output":20600,"total":140600}}"""))

        assertEquals(120_000L, spend?.input)
        assertEquals(20_600L, spend?.output)
        assertEquals(140_600L, spend?.total)
    }

    @Test
    fun `a total that is missing is the sum of the two directions`() {
        val spend = BoardSpend.of(card("""{"tokenUsage":{"input":1000,"output":500}}"""))

        assertEquals(1_500L, spend?.total)
    }

    @Test
    fun `an older card carries a plain count, which is all it can say`() {
        val spend = BoardSpend.of(card("""{"tokensUsed":4200}"""))

        assertEquals(4_200L, spend?.total)
        assertEquals(0L, spend?.input)
    }

    @Test
    fun `a card that never ran spends nothing, rather than zero tokens`() {
        assertNull(BoardSpend.of(card("""{"status":"pending"}""")))
        assertNull(BoardSpend.of(card("""{"tokenUsage":{"input":0,"output":0,"total":0}}""")))
        assertNull(BoardSpend.of(null))
    }

    @Test
    fun `the cost is the web app's estimate`() {
        // 120k in at $3/M + 20.6k out at $15/M = 0.36 + 0.309
        val spend = TokenSpend(input = 120_000, output = 20_600, total = 140_600)
        assertEquals(0.669, BoardSpend.cost(spend), 1e-9)
        assertEquals(0.0, BoardSpend.cost(null), 1e-9)
    }

    @Test
    fun `the counters separate what runs, what waits, what ended, and what asks`() {
        val cards = listOf(
            card("""{"status":"running","tokenUsage":{"input":1000000,"output":0}}"""),
            card("""{"status":"pending"}"""),
            card("""{"status":"paused"}"""),
            card("""{"status":"question"}"""),
            card("""{"status":"done","tokenUsage":{"input":0,"output":1000000,"total":1000000}}"""),
            card("""{"status":"failed"}"""),
            card("""{"status":"failed","error":"No LLM providers available"}"""),
        )

        val metrics = BoardMetricsOf.of(cards)

        assertEquals(1, metrics.running)
        // pending and paused are both waiting for the owner.
        assertEquals(2, metrics.pending)
        assertEquals(1, metrics.done)
        assertEquals(2, metrics.failed)
        assertEquals(1, metrics.needsInput)
        assertEquals(7, metrics.total)
        assertEquals(2_000_000L, metrics.tokens)
        // $3 for the million in, $15 for the million out.
        assertEquals(18.0, metrics.cost, 1e-9)
    }

    @Test
    fun `an empty board is empty, not four zeros`() {
        val metrics = BoardMetricsOf.of(emptyList())

        assertTrue(metrics.empty)
        assertEquals(0, metrics.total)
        assertEquals(0L, metrics.tokens)
    }

    @Test
    fun `tokens and costs print the way the rest of the app prints them`() {
        assertEquals("140,6k", formatTokens(140_600))
        assertEquals("$0,47", formatCost(0.47))
        assertEquals("$12", formatCost(12.0))
        // A cost that would round to zero must not read as free.
        assertEquals("<$0,001", formatCost(0.0009))
        assertEquals("$0", formatCost(0.0))
    }
}
