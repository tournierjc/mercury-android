package io.github.tournierjc.mercury.bots

import io.github.tournierjc.mercury.data.dto.BotRunDto

/**
 * The context gauge of a bot conversation, read from the journal.
 *
 * A bot answers each message from a fresh context plus its own notes and journal — its turns run
 * outside the session store, so there is no keeper window to compact here. What the owner can
 * still ask of a gauge is « what has this conversation cost so far », and the journal already
 * carries it: every run records the tokens it read and wrote. The daemon answers `/compact` in
 * this thread with the same numbers (its `threadHistoryTokens` reads the very transcripts); the
 * bar mirrors that reading between turns, from the records the app already holds.
 *
 * A slash answer (`recordDirectAnswer`) journals a run with no tokens: it counts as a turn but
 * adds nothing to the cost, which is the truth of it.
 */
object BotGauge {

    /** What the conversation has cost, and how many turns it holds. */
    data class View(val tokens: Long, val turns: Int) {
        /** Nothing to draw: a conversation the model has not read or written anything for. */
        val isEmpty: Boolean get() = tokens <= 0
    }

    fun view(records: List<BotRunDto>): View = View(
        tokens = records.sumOf { it.tokensIn + it.tokensOut }.coerceAtLeast(0L),
        turns = records.count { it.runId.isNotBlank() },
    )
}
