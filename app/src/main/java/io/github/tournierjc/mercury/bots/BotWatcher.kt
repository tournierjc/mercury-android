package io.github.tournierjc.mercury.bots

import io.github.tournierjc.mercury.data.BotEvent
import io.github.tournierjc.mercury.data.dto.BotsResponse
import io.github.tournierjc.mercury.data.dto.asSnapshot
import io.github.tournierjc.mercury.data.BotJson
import io.github.tournierjc.mercury.data.MercuryApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

data class ActivityLine(val botId: String, val label: String, val kind: String, val status: String)

/**
 * The turn a bot is writing right now.
 *
 * `text` is the **tail** of the answer — the daemon sends the last 600 characters of the
 * visible text every few hundred milliseconds, not the whole message — so a long answer
 * scrolls in its bubble instead of growing forever. Good enough to read a bot think;
 * deliberately not a claim to be a full transcript.
 */
data class LiveTurn(
    val jobId: String,
    val text: String,
    val reasoning: String = "",
    val running: Boolean = true,
)

data class BotWatchState(
    val bots: List<JsonObject> = emptyList(),
    val queue: BotEvent.QueueSnapshot = BotEvent.QueueSnapshot(),
    val connected: Boolean = false,
    val activity: Map<String, String> = emptyMap(),
    /** Per-bot, the turn being written — what the conversation screen streams. */
    val live: Map<String, LiveTurn> = emptyMap(),
    val recent: List<ActivityLine> = emptyList(),
    val error: String? = null,
) {
    fun needsYou(): List<JsonObject> = bots.filter { BotJson.needsYou(it) }
    fun working(): List<JsonObject> = bots.filter { BotJson.state(it) == "running" || BotJson.state(it) == "queued" }
}

/**
 * Holds `/api/bots/events` open for the whole process and turns roster diffs into
 * notifications. Passive state ("Scribe is reading report.md") lands in [activity]; the
 * owner-facing signal ("Scribe needs you") goes to the notification shade.
 *
 * Android may kill the process, and this watcher does not hold a foreground service: the
 * roster is live while the app is alive. Push would need a server-side transport that
 * Mercury 1.4.2 does not provide.
 */
class BotWatcher(
    private val api: MercuryApi,
    private val notifier: BotNotifier,
    private val scope: CoroutineScope,
    /** Qui a le droit de sonner : le leader d'une flotte, ou un bot auquel on a parlé. */
    private val memory: BotNotifyMemory? = null,
) {

    private val _state = MutableStateFlow(BotWatchState())
    val state: StateFlow<BotWatchState> = _state.asStateFlow()

    private var job: Job? = null

    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        notifier.ensureChannel()
        job = scope.launch {
            var backoff = 2_000L
            while (true) {
                try {
                    api.botEvents().collect { event ->
                        backoff = 2_000L
                        when (event) {
                            is BotEvent.Roster -> {
                                val previous = _state.value.bots
                                _state.update { it.copy(bots = event.bots, queue = event.queue, connected = true, error = null) }
                                BotJson.attention(previous, event.bots).forEach(notifier::notify)
                            }
                            is BotEvent.Activity -> {
                                _state.update { current ->
                                    val writing = event.textTail.isNotBlank() || event.reasoningTail.isNotBlank()
                                    val finished = event.kind == "turn-end" || event.kind == "turn-end-error"
                                    current.copy(
                                        activity = if (event.label.isBlank()) current.activity else current.activity + (event.botId to event.label),
                                        recent = (listOf(ActivityLine(event.botId, event.label, event.kind, event.status)) + current.recent).take(40),
                                        live = when {
                                            writing -> current.live + (event.botId to LiveTurn(
                                                jobId = event.jobId,
                                                text = event.textTail,
                                                reasoning = event.reasoningTail,
                                                running = !finished,
                                            ))
                                            // The turn ended without a text tail (a tool-only turn):
                                            // keep nothing to stream, but do not leave a bubble
                                            // pretending to still be writing.
                                            finished -> current.live - event.botId
                                            else -> current.live
                                        },
                                    )
                                }
                                // Outside the atomic update on purpose: `update` may re-run its
                                // lambda under contention, and a notification posted twice is worse
                                // than one posted a moment late. A finished turn that wrote something
                                // is a message that arrived; the tail is the last 600 characters the
                                // daemon sends, enough to know what it says.
                                val finished = event.kind == "turn-end" || event.kind == "turn-end-error"
                                if (finished && event.textTail.isNotBlank()) {
                                    val bot = _state.value.bots.firstOrNull { BotJson.id(it) == event.botId }
                                    // A fleet job runs one turn per teammate; ringing for each of
                                    // them is the same news six times, and the owner ends up
                                    // muting the app. The lead speaks for its crew.
                                    val rings = BotNotifyPolicy.shouldPost(
                                        isLead = BotJson.isLead(bot),
                                        spokenTo = memory?.spokenTo(event.botId) ?: false,
                                        foreground = AppVisibility.foreground,
                                        openBotId = AppVisibility.openBotId,
                                        botId = event.botId,
                                    )
                                    if (rings) {
                                        val name = bot?.let { BotJson.name(it) }?.ifBlank { null }
                                        notifier.message(event.botId, name ?: event.botId, event.textTail)
                                    }
                                }
                            }
                            is BotEvent.Notice -> if (event.message.isNotBlank()) notifier.notice(event.message)
                            is BotEvent.Unknown -> Unit
                        }
                    }
                    _state.update { it.copy(connected = false) }
                } catch (e: Exception) {
                    _state.update { it.copy(connected = false, error = e.message) }
                }
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(60_000L)
            }
        }
    }

    /**
     * Applies a roster read over HTTP, on top of the live feed.
     *
     * The feed is the usual source, but it only speaks when the daemon broadcasts: after a rename
     * the drawer and the header kept the old name, and the refresh button fetched the list to throw
     * it away (it cleared the error banner and nothing else, so it looked dead). Two callers, one
     * way in: the button, and the reply to any write that changes what the roster shows.
     */
    fun ingest(roster: BotsResponse) {
        _state.update {
            it.copy(
                bots = roster.bots,
                queue = roster.queue.asSnapshot(),
                error = null,
            )
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
