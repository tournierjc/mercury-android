package io.github.tournierjc.mercury.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * What a card has spent, as the server reports it.
 *
 * Two shapes exist in the wild — a `tokenUsage` object on a card that ran, and a plain `tokensUsed`
 * count on an older one — and the phone showed neither, only the card's *budget*. A card that had
 * burned 140 600 tokens read as the card that was allowed to, which is the opposite of the question
 * an owner asks of a board.
 */
data class TokenSpend(val input: Long, val output: Long, val total: Long) {
    val known: Boolean get() = total > 0
}

object BoardSpend {

    /**
     * The web app's estimate, so both clients print the same figure for the same card: $3 per
     * million tokens in, $15 per million out. It is an estimate of a rate, not an invoice — no
     * provider's real prices are known here.
     */
    const val INPUT_PER_MILLION = 3.0
    const val OUTPUT_PER_MILLION = 15.0

    fun of(card: JsonObject?): TokenSpend? {
        val usage = card?.get("tokenUsage") as? JsonObject
        if (usage != null) {
            val input = number(usage["input"])
            val output = number(usage["output"])
            val total = number(usage["total"]).takeIf { it > 0 } ?: (input + output)
            if (total > 0) return TokenSpend(input, output, total)
        }
        val used = number(card?.get("tokensUsed"))
        return if (used > 0) TokenSpend(input = 0, output = 0, total = used) else null
    }

    fun cost(spend: TokenSpend?): Double {
        if (spend == null) return 0.0
        return spend.input / 1_000_000.0 * INPUT_PER_MILLION +
            spend.output / 1_000_000.0 * OUTPUT_PER_MILLION
    }

    private fun number(value: Any?): Long = (value as? JsonPrimitive)?.longOrNull ?: 0L
}

/**
 * The four numbers the web app prints above a board, plus the tokens and the cost.
 *
 * Counted from the cards the phone already holds rather than read from a summary field: the board
 * stream delivers the cards, and a second source would be one more thing that can disagree. `paused`
 * and `pending` are both waiting for the owner, and `question` waits louder, so it is its own count
 * — that is the banner the web app shows above the columns.
 */
data class BoardMetrics(
    val running: Int = 0,
    val pending: Int = 0,
    val done: Int = 0,
    val failed: Int = 0,
    val needsInput: Int = 0,
    val tokens: Long = 0,
    val cost: Double = 0.0,
) {
    val total: Int get() = running + pending + done + failed + needsInput
    val empty: Boolean get() = total == 0
}

object BoardMetricsOf {

    fun of(cards: List<JsonObject>): BoardMetrics {
        var running = 0
        var pending = 0
        var done = 0
        var failed = 0
        var needsInput = 0
        var tokens = 0L
        var cost = 0.0
        for (card in cards) {
            when (BoardJson.status(card)) {
                "running" -> running++
                "pending", "paused" -> pending++
                "done" -> done++
                "failed" -> failed++
                "question" -> needsInput++
                else -> pending++
            }
            val spend = BoardSpend.of(card)
            tokens += spend?.total ?: 0L
            cost += BoardSpend.cost(spend)
        }
        return BoardMetrics(running, pending, done, failed, needsInput, tokens, cost)
    }
}
