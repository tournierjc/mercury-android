package io.github.tournierjc.mercury.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Events of `/api/bots/events` (SSE). The channel emits:
 *  - `bot_roster` on connect and after every change: `{bots:[…], queue:{…}}`
 *  - `bot_activity` on every bot turn step: `{botId, jobId, kind, label, detail, status}`
 *  - `bot_notice` for free-form messages ("Fleet ready — 3 crew members created")
 *  - a `: keepalive` comment every ~20 s (dropped by the parser)
 */
sealed interface BotEvent {

    data class Roster(val bots: List<JsonObject>, val queue: QueueSnapshot) : BotEvent

    /** `kind`: turn-start, thinking, step, tool, tool-head, tool-out, turn-end… */
    data class Activity(
        val botId: String,
        val jobId: String,
        val kind: String,
        val label: String,
        val detail: String,
        val status: String,
        val stepIndex: Int = 0,
        val elapsedMs: Long = 0,
        /** Present on `thinking` events: the tail of the model's reasoning. */
        val reasoningTail: String = "",
        /** Present on `thinking`/`turn-end`: the tail of the visible answer. */
        val textTail: String = "",
        /**
         * What put this turn in the queue: `chat` (the owner wrote in the app), `cron` (a scheduled
         * routine), `mailbox` (another bot handed work over)…
         *
         * Empty from a daemon that predates it, which the notification policy reads as "unknown".
         */
        val trigger: String = "",
        /** `chat` answers the owner; `work` delivers. */
        val mode: String = "",
    ) : BotEvent

    data class Notice(val message: String) : BotEvent

    /**
     * `bot_approval` — a bot asks the owner mid-run ("Write to file: …?"). The daemon's
     * fail-closed clock (auto-deny at `expiresAt`) makes this the one event that cannot wait
     * for the next roster push: a question that only shows up fifteen minutes later has already
     * been refused by then. `settled` events are parsed but ignored by the watcher — the
     * answer's own consequence (the run resuming or dying) comes through the roster.
     */
    data class Approval(
        val id: String,
        val botId: String,
        val botName: String,
        val runId: String,
        val prompt: String,
        val expiresAt: Long = 0,
        /** `new` raises; `settled` is the daemon telling the question is over. */
        val phase: String,
    ) : BotEvent

    data class Unknown(val type: String) : BotEvent

    data class QueueSnapshot(val pending: Int = 0, val claimed: Int = 0, val dlq: Int = 0)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parse(type: String?, data: String): BotEvent {
            val payload = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull()
                ?: return Unknown(type.orEmpty())
            return when (type) {
                "bot_roster" -> Roster(
                    bots = BotJson.roster(payload),
                    queue = QueueSnapshot(
                        pending = int(payload["queue"], "pending"),
                        claimed = int(payload["queue"], "claimed"),
                        dlq = int(payload["queue"], "dlq"),
                    ),
                )
                "bot_activity" -> Activity(
                    botId = text(payload, "botId"),
                    jobId = text(payload, "jobId"),
                    kind = text(payload, "kind"),
                    label = text(payload, "label"),
                    detail = text(payload, "detail"),
                    status = text(payload, "status"),
                    stepIndex = intValue(payload["stepIndex"]),
                    elapsedMs = long(payload, "elapsedMs"),
                    reasoningTail = text(payload, "reasoningTail"),
                    textTail = text(payload, "textTail"),
                    trigger = text(payload, "trigger"),
                    mode = text(payload, "mode"),
                )
                "bot_notice" -> Notice(text(payload, "message"))
                "bot_approval" -> Approval(
                    id = text(payload, "id"),
                    botId = text(payload, "botId"),
                    botName = text(payload, "botName"),
                    runId = text(payload, "runId"),
                    prompt = text(payload, "prompt"),
                    expiresAt = long(payload, "expiresAt"),
                    phase = text(payload, "type"),
                )
                else -> Unknown(type.orEmpty())
            }
        }

        private fun text(node: JsonObject, key: String): String =
            (node[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

        private fun int(node: kotlinx.serialization.json.JsonElement?, key: String): Int =
            intValue((node as? JsonObject)?.get(key))

        private fun intValue(node: kotlinx.serialization.json.JsonElement?): Int =
            (node as? JsonPrimitive)?.intOrNull ?: 0

        private fun long(node: JsonObject, key: String): Long =
            (node[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toLong() } ?: 0L
    }
}
