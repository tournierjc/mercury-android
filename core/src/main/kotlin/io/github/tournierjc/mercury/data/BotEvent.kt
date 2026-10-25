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
    ) : BotEvent

    data class Notice(val message: String) : BotEvent

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
                    elapsedMs = long(payload["elapsedMs"]),
                    reasoningTail = text(payload, "reasoningTail"),
                    textTail = text(payload, "textTail"),
                )
                "bot_notice" -> Notice(text(payload, "message"))
                else -> Unknown(type.orEmpty())
            }
        }

        private fun text(node: JsonObject, key: String): String =
            (node[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

        private fun int(node: kotlinx.serialization.json.JsonElement?, key: String): Int =
            intValue((node as? JsonObject)?.get(key))

        private fun intValue(node: kotlinx.serialization.json.JsonElement?): Int =
            (node as? JsonPrimitive)?.intOrNull ?: 0

        private fun long(node: kotlinx.serialization.json.JsonElement?): Long =
            (node as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toLong() } ?: 0L
    }
}
