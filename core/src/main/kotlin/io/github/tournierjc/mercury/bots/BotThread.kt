package io.github.tournierjc.mercury.bots

import io.github.tournierjc.mercury.data.ToolStep
import io.github.tournierjc.mercury.data.dto.BotRunDto
import io.github.tournierjc.mercury.data.dto.TraceStepDto

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
    /**
     * The moment the daemon dated this bubble, epoch milliseconds from the run transcript's
     * `timestamp` or, failing that, from the journal — or `null`
     * when the turn has not reached the journal yet. A turn still queued has no answer time
     * (SPECS/115 §3.4: no local clock, no invented hour), so the bubble draws no hour at all.
     */
    val at: Long? = null,
    val state: String = "",
    val streaming: Boolean = false,
    /**
     * The tool calls this turn made, in order — the transcript's `trace`. The main conversation
     * draws them above the bubble; a bot's turn is no different, and without them a turn that ran
     * forty commands reads as one paragraph that came from nowhere.
     */
    val steps: List<ToolStep> = emptyList(),
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
     * @param dates `runId` → the transcript's own `timestamp`, epoch milliseconds. The run
     *   transcript dates the question better than the journal's enqueue time; a run the
     *   transcript did not date keeps the journal's clock.
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
        /**
         * `runId` → the transcript's `timestamp`, epoch milliseconds (SPECS/115 §3.4). A key
         * that is present with a `null` value is a transcript the daemon wrote *undated*: no
         * hour line at all. A key that is absent is a transcript older than the field: the
         * turn's own clock.
         */
        dates: Map<String, Long?> = emptyMap(),
        liveJobId: String? = null,
        liveTail: String = "",
        liveRunning: Boolean = true,
        /** `runId` → the transcript's `trace`: what the turn actually did, tool by tool. */
        traces: Map<String, List<TraceStepDto>> = emptyMap(),
    ): List<BotMessage> {
        val messages = mutableListOf<BotMessage>()
        records
            .filter { it.runId.isNotBlank() }
            .sortedBy { it.startedAt }
            .forEach { record ->
                val prompt = prompts[record.runId].orEmpty().trim()
                // The journal's `startedAt` is the enqueue time; `turnStartedAt` is when the bot
                // actually began. The transcript's own `timestamp` is the turn's date as the
                // daemon wrote it, so it wins when the field is there. Either is the server's
                // clock; a missing one is not 1970 and not "now".
                val journalAt = (record.turnStartedAt.takeIf { it > 0 } ?: record.startedAt)
                    .takeIf { it > 0 }
                // Three shapes, three rules (§3.4): the transcript's date when it wrote a
                // readable one; the turn's own clock when the field is absent — every transcript
                // older than the field —; no hour at all when the daemon wrote it undated, since
                // a bubble dated from the run's clock would be the app inventing a time.
                val turnAt = if (dates.containsKey(record.runId)) {
                    dates[record.runId]?.takeIf { it > 0 }
                } else {
                    journalAt
                }
                if (prompt.isNotEmpty()) {
                    messages += BotMessage(
                        key = "${record.runId}-me",
                        fromBot = false,
                        text = prompt,
                        at = turnAt,
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
                    // The answer exists at the end of the turn, not at its start.
                    at = turnAt?.plus(record.durationMs),
                    state = record.state,
                    streaming = streaming,
                    steps = stepsOf(traces[record.runId]),
                )
            }
        // A job that has not reached the journal yet: the optimistic turn, streaming or not.
        // It has no date of its own, and the phone's clock is not the server's (SPECS/115 §3.4):
        // this bubble draws no hour, and the journal dates it once the run lands.
        if (liveJobId != null && records.none { it.runId == liveJobId } && liveTail.isNotBlank()) {
            messages += BotMessage(
                key = "$liveJobId-live",
                fromBot = true,
                text = liveTail,
                at = null,
                streaming = liveRunning,
            )
        }
        return messages
    }

    /**
     * The transcript's `trace` as the chat's step pills. A failed call keeps its place — a turn
     * that stumbled is not a turn that did less — so its pill carries the cross the owner reads.
     */
    private fun stepsOf(trace: List<TraceStepDto>?): List<ToolStep> =
        trace.orEmpty()
            .filter { it.name.isNotBlank() }
            .map { step ->
                ToolStep(
                    tool = step.name,
                    label = if (step.ok) step.name else "✗ ${step.name}",
                    summary = step.arg.takeIf { it.isNotBlank() },
                )
            }

    /** The last turn still writing, if the daemon says one is. */
    fun isStreaming(messages: List<BotMessage>): Boolean = messages.lastOrNull()?.streaming == true
}
