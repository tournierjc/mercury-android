package io.github.tournierjc.mercury.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class RawSseEvent(val type: String?, val data: String)

/** Events the app understands. Anything else is surfaced as [ChatEvent.Ignored]. */
sealed interface ChatEvent {
    data class Connected(val provider: String, val model: String) : ChatEvent
    data class TextDelta(val text: String) : ChatEvent
    data class TextDone(val fullText: String, val elapsedMs: Long?) : ChatEvent
    data object Thinking : ChatEvent
    data class StepStart(val tool: String, val label: String) : ChatEvent
    data class StepDone(val tool: String, val summary: String) : ChatEvent
    data class PermissionRequest(val id: String, val prompt: String, val options: List<String>) : ChatEvent
    data class ContinueRequest(val id: String, val question: String) : ChatEvent
    data class SessionSwitched(val sessionId: String, val title: String) : ChatEvent
    data class Failure(val message: String) : ChatEvent
    data class Ignored(val type: String?) : ChatEvent
}

/**
 * Maps Mercury wire events to [ChatEvent].
 *
 * Only the events the app renders are decoded; unknown types are ignored rather
 * than guessed, so a Mercury release adding new events cannot crash the client.
 */
object MercuryEventParser {

    val defaultJson: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun parse(raw: RawSseEvent, json: Json = defaultJson): ChatEvent {
        val obj = runCatching { json.parseToJsonElement(raw.data).jsonObject }.getOrNull()
            ?: return ChatEvent.Ignored(raw.type)
        return when (raw.type) {
            "connected" -> ChatEvent.Connected(
                provider = obj.string("provider"),
                model = obj.string("model"),
            )

            "text_delta" -> ChatEvent.TextDelta(obj.string("text"))
            "text_done" -> ChatEvent.TextDone(
                fullText = obj.string("fullText"),
                elapsedMs = obj["elapsedMs"]?.jsonPrimitive?.longOrNull,
            )

            "thinking" -> ChatEvent.Thinking
            "step_start" -> ChatEvent.StepStart(
                tool = obj.string("tool"),
                label = obj.string("label").ifBlank { obj.string("tool") },
            )

            "step_done" -> ChatEvent.StepDone(
                tool = obj.string("tool"),
                summary = obj.string("summary"),
            )

            "permission_request" -> ChatEvent.PermissionRequest(
                id = obj.string("id"),
                prompt = obj.string("prompt"),
                options = obj.stringList("options"),
            )

            "permission_continue" -> ChatEvent.ContinueRequest(
                id = obj.string("id"),
                question = obj.string("question"),
            )

            "session_switched" -> ChatEvent.SessionSwitched(
                sessionId = obj.string("id"),
                title = obj.string("title").ifBlank { obj.string("alias") },
            )

            "error" -> ChatEvent.Failure(obj.string("message").ifBlank { "Unknown error" })
            else -> ChatEvent.Ignored(raw.type)
        }
    }

    private fun JsonObject.string(key: String): String =
        runCatching { this[key]?.jsonPrimitive?.content ?: "" }.getOrDefault("")

    private fun JsonObject.stringList(key: String): List<String> = runCatching {
        val element = this[key] ?: return emptyList()
        (element as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()
    }.getOrDefault(emptyList())
}
