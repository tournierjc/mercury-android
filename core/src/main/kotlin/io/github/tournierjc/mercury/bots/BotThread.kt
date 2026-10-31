package io.github.tournierjc.mercury.bots

import io.github.tournierjc.mercury.data.ToolStep
import io.github.tournierjc.mercury.data.dto.BotRunDto
import io.github.tournierjc.mercury.data.dto.ReasoningBlockDto
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
    val at: Long,
    val state: String = "",
    val streaming: Boolean = false,
    /**
     * The tool calls this turn made, in order — the transcript's `trace`. The main conversation
     * draws them above the bubble; a bot's turn is no different, and without them a turn that ran
     * forty commands reads as one paragraph that came from nowhere.
     */
    val steps: List<ToolStep> = emptyList(),
    /**
     * What the bot thought on its way to that answer, step by step, from the transcript.
     *
     * The conversation showed the calls and the reply, which is the *what*: a turn that read six
     * files and answered "rien à signaler" left the owner unable to tell a check from a guess. Only
     * the transcript's own reasoning is here — never invented, never the daemon's live tail.
     */
    val reasoning: List<ReasoningBlockDto> = emptyList(),
    /** The daemon kept only the newest of the thinking: say so rather than let it read complete. */
    val reasoningTruncated: Boolean = false,
    /**
     * What the turn is doing while it has not written anything yet, in the daemon's words.
     *
     * Only ever set on a live turn with `state == "activity"`: it is a phase, not a message, and it
     * must never be mistaken for one after the turn lands.
     */
    val activity: String = "",
    /**
     * Which bot this message belongs to — set on the optimistic echo only.
     *
     * The echo now outlives a refresh, so it must say who it was addressed to: without this, a
     * message queued for one teammate would resurface in another's conversation the moment the
     * owner switched screens.
     */
    val botId: String = "",
    /**
     * The text is the journal's summary, not the transcript: the daemon cuts it at 300
     * characters, which lands in the middle of a fenced block — an `openui` answer comes back
     * as half a spec, and half a spec is not a layout. The screen says so instead of showing it.
     */
    val truncated: Boolean = false,
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
     * True when the journal names a run the thread was not built from.
     *
     * The screen used to repaint only when the live feed said a turn had stopped writing — and the
     * feed is the part that goes missing (a dropped stream, a turn that finished while the phone was
     * away, a process the system killed). The journal writes a record exactly once, when the turn is
     * over, so its newest line is the durable signal that there is something new to read.
     */
    fun isStale(knownRunId: String?, newestRunId: String?): Boolean =
        !newestRunId.isNullOrBlank() && newestRunId != knownRunId

    /**
     * Which optimistic turns to keep showing.
     *
     * The owner's message is drawn the moment the daemon accepts it, before the journal knows that
     * run exists — and every refresh used to wipe that echo, so switching windows mid-turn made the
     * message vanish until the answer came. A pending turn is dropped only when the journal holds a
     * record for its job: that is the one fact proving the real message has taken its place.
     */
    fun keepPending(
        pending: List<BotMessage>,
        records: List<BotRunDto>,
        now: Long = System.currentTimeMillis(),
    ): List<BotMessage> {
        if (pending.isEmpty()) return pending
        val landed = records.mapNotNull { it.runId.takeIf { id -> id.isNotBlank() } }.toSet()
        return pending.filter { echo ->
            echo.key.removeSuffix("-me") !in landed &&
                // A turn that never lands (killed before the journal, dropped from the queue) would
                // otherwise sit there forever as a message the owner thinks is still on its way.
                now - echo.at < ECHO_MAX_AGE_MS
        }
    }

    /** How long an optimistic turn may wait for its journal record before it is given up on. */
    private const val ECHO_MAX_AGE_MS = 2 * 60 * 60 * 1000L

    /**
     * @param records journal lines for one bot, any order.
     * @param prompts `runId` → the task the owner dispatched for that run.
     * @param outputs `runId` → the full answer, when the transcript is at hand.
     * @param liveJobId the last turn the daemon reported, if any.
     * @param liveTail the tail of the text the bot wrote, if any.
     * @param liveRunning whether the daemon still calls that turn running. Only consulted for a
     *   job the journal has no record of yet: the journal writes a run's record **once, when the
     *   turn is over**, so the presence of a record is itself the proof that the answer is final.
     * @param liveActivity what the daemon says the turn is doing right now ("thinking", "running a
     *   command"). A bot that has not written a word yet is still a bot at work, and the owner used
     *   to see the phase scroll in the title bar while the conversation stayed empty.
     */
    fun build(
        records: List<BotRunDto>,
        prompts: Map<String, String>,
        /** `runId` → the full answer, from the run transcript. */
        outputs: Map<String, String> = emptyMap(),
        liveJobId: String? = null,
        liveTail: String = "",
        liveRunning: Boolean = true,
        /** The phase the daemon reports for that turn, in its own words. */
        liveActivity: String = "",
        /** `runId` → the transcript's `trace`: what the turn actually did, tool by tool. */
        traces: Map<String, List<TraceStepDto>> = emptyMap(),
        /** `runId` → the transcript's per-step `reasoning`. */
        reasoning: Map<String, List<ReasoningBlockDto>> = emptyMap(),
        /** `runIds` whose thinking the daemon bounded. */
        reasoningTruncated: Set<String> = emptySet(),
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
                // A record here means the turn is over — the daemon appends it after the turn,
                // never during it. Streaming its 600-character tail on that record used to keep a
                // finished turn marked as still writing, and hid the full reply sitting right
                // there in the transcript: a missed `turn-end` (a dropped stream, a process the
                // system killed) left the tail on screen as if it were the message.
                val streaming = false
                val full = outputs[record.runId]?.takeIf { it.isNotBlank() }
                val reply = when {
                    streaming -> liveTail
                    // The journal truncates `summary` to 300 characters; the transcript keeps
                    // the whole answer, which is what a conversation must show.
                    full != null -> full
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
                    // Only a summary can be cut: an error line and an empty turn are what they are.
                    truncated = !streaming && full == null && record.summary.isNotBlank(),
                    steps = stepsOf(traces[record.runId]),
                    reasoning = reasoning[record.runId].orEmpty(),
                    reasoningTruncated = record.runId in reasoningTruncated,
                )
            }
        // A job that has not reached the journal yet: the optimistic turn, streaming or not — and
        // when the bot has not written a word yet, the phase it is in, so the conversation answers
        // "it is working" instead of showing nothing at all.
        if (liveJobId != null && records.none { it.runId == liveJobId } && (liveTail.isNotBlank() || liveActivity.isNotBlank())) {
            val waiting = liveTail.isBlank()
            messages += BotMessage(
                key = "$liveJobId-live",
                fromBot = true,
                text = liveTail,
                at = System.currentTimeMillis(),
                // "activity": the turn exists but has produced no text; the screen draws the phase.
                state = if (waiting) "activity" else "",
                streaming = liveRunning,
                activity = if (waiting) liveActivity else "",
            )
        }
        return messages
    }

    /**
     * The transcript's `trace` as the chat's steps.
     *
     * The transcript gives the argument (`ls /work`, a path) and whether the call worked — never a
     * summary of the result. The step therefore carries the argument as its label and the failure
     * as a flag: the row then reads "💻 run_command" over its command, which is the shape the owner
     * knows from a terminal, instead of the tool's name printed twice.
     */
    private fun stepsOf(trace: List<TraceStepDto>?): List<ToolStep> =
        trace.orEmpty()
            .filter { it.name.isNotBlank() }
            .map { step ->
                ToolStep(
                    tool = step.name,
                    label = step.arg,
                    failed = !step.ok,
                )
            }

    /** The last turn still writing, if the daemon says one is. */
    fun isStreaming(messages: List<BotMessage>): Boolean = messages.lastOrNull()?.streaming == true
}
