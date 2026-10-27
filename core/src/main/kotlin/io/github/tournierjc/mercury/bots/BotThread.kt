package io.github.tournierjc.mercury.bots

import io.github.tournierjc.mercury.data.dto.BotRunDto

/**
 * One bubble of a bot's conversation.
 *
 * `streaming` is true while the turn is still running: the text is then the tail of what the
 * bot is writing (the daemon sends a rolling 600-character tail in `bot_activity`), not the
 * final answer.
 */
data class BotMessage(
    val key: String,
    val fromBot: Boolean,
    val text: String,
    val at: Long,
    val state: String = "",
    val streaming: Boolean = false,
)

/**
 * A bot's work, read as a conversation.
 *
 * Mercury runs a bot message as a job: the question is the job's prompt (kept in the run
 * transcript), the answer is the run's `summary` (kept in the journal). Neither alone is a
 * conversation, and the two arrive from different places — the journal is ordered by start
 * time and the prompts are fetched per run. This joins them into the list a chat screen can
 * draw, and folds the live streaming tail into the turn it belongs to instead of showing it
 * twice.
 */
object BotThread {

    /**
     * @param records journal lines for one bot, any order.
     * @param prompts `runId` → the task the owner dispatched for that run.
     * @param outputs `runId` → the full answer, when the transcript is at hand.
     * @param liveJobId the last turn the daemon reported, if any.
     * @param liveTail the tail of the text the bot wrote, if any.
     * @param liveRunning false once that turn has ended: the tail is then a memory of the
     *   answer to show until the journal catches up, not something still being written.
     */
    fun build(
        records: List<BotRunDto>,
        prompts: Map<String, String>,
        /** `runId` → the full answer, from the run transcript. */
        outputs: Map<String, String> = emptyMap(),
        liveJobId: String? = null,
        liveTail: String = "",
        liveRunning: Boolean = true,
    ): List<BotMessage> {
        val messages = mutableListOf<BotMessage>()
        records
            .filter { it.runId.isNotBlank() }
            .sortedBy { it.startedAt }
            .forEach { record ->
                val prompt = prompts[record.runId].orEmpty().trim()
                if (prompt.isNotEmpty()) {
                    messages += BotMessage(
                        key = "${record.runId}-me",
                        fromBot = false,
                        text = prompt,
                        at = record.startedAt,
                    )
                }
                // A streaming turn has an answer in progress: the journal summary is either
                // absent or stale, so the live tail is what the owner should be reading.
                val streaming = liveRunning && liveJobId != null && record.runId == liveJobId && liveTail.isNotBlank()
                val reply = when {
                    streaming -> liveTail
                    // The journal truncates `summary` to 300 characters; the transcript keeps
                    // the whole answer, which is what a conversation must show.
                    outputs[record.runId]?.isNotBlank() == true -> outputs.getValue(record.runId)
                    record.summary.isNotBlank() -> record.summary
                    record.error.isNotBlank() -> record.error
                    // A run that produced neither a summary nor an error was still a turn: say
                    // so rather than dropping the bubble and leaving a question unanswered.
                    else -> ""
                }
                messages += BotMessage(
                    key = "${record.runId}-bot",
                    fromBot = true,
                    text = reply,
                    at = record.startedAt + record.durationMs,
                    state = record.state,
                    streaming = streaming,
                )
            }
        // A job that has not reached the journal yet: the optimistic turn, streaming or not.
        if (liveJobId != null && records.none { it.runId == liveJobId } && liveTail.isNotBlank()) {
            messages += BotMessage(
                key = "$liveJobId-live",
                fromBot = true,
                text = liveTail,
                at = System.currentTimeMillis(),
                streaming = liveRunning,
            )
        }
        return messages
    }

    /** The last turn still writing, if the daemon says one is. */
    fun isStreaming(messages: List<BotMessage>): Boolean = messages.lastOrNull()?.streaming == true
}
