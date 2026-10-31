package io.github.tournierjc.mercury.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class RawSseEvent(val type: String?, val data: String)

/**
 * One answer an interaction offers: [value] is what `POST /api/chat/permission/:id` must receive,
 * [label] is the wording the daemon wants drawn on the button.
 *
 * The web channel does not speak one shape for every prompt: `presentChoicePrompt` emits
 * `Array<{value,label}>` (`src/channels/web.ts`), while the tool-approval, "keep going?" and
 * auto-approval prompts still send bare strings. Modelling both here keeps the invariant
 * structural — the app posts [value] and displays [label] whenever the server wrote one — instead
 * of re-deriving it from a string at the call site. [label] is `null`, not `""`, when the server
 * said nothing: the screen then falls back to its own wording for the value.
 */
data class ChoiceOption(val value: String, val label: String? = null)

/** Convenience for the daemon's bare-string prompts and their documented fallbacks. */
fun optionsOf(vararg values: String): List<ChoiceOption> = values.map { ChoiceOption(it) }

/** Events the app understands. Anything else is surfaced as [ChatEvent.Ignored]. */
sealed interface ChatEvent {
    data class Connected(val provider: String, val model: String) : ChatEvent
    data class TextDelta(val text: String) : ChatEvent
    data class TextDone(val fullText: String, val elapsedMs: Long?) : ChatEvent
    data object Thinking : ChatEvent
    data class StepStart(val tool: String, val label: String) : ChatEvent
    data class StepDone(val tool: String, val summary: String) : ChatEvent
    /** Approval of a tool call: options are `yes` / `always` / `no`. */
    data class PermissionRequest(
        val id: String,
        val prompt: String,
        val options: List<ChoiceOption>,
    ) : ChatEvent

    /** "Keep going?" after a loop guard tripped. */
    data class ContinueRequest(
        val id: String,
        val question: String,
        val options: List<ChoiceOption> = optionsOf("yes", "no"),
    ) : ChatEvent

    /** Free-form question the agent needs answered before it can continue. */
    data class ChoicePrompt(
        val id: String,
        val question: String,
        /** Exactly the server's list: this prompt has no documented fallback to invent. */
        val options: List<ChoiceOption>,
    ) : ChatEvent

    /** The runtime asks whether to auto-approve every tool call from now on. */
    data class PermissionModeRequest(
        val id: String,
        val options: List<ChoiceOption>,
    ) : ChatEvent

    data class SessionSwitched(val sessionId: String, val title: String) : ChatEvent
    data class ProviderInfo(val name: String, val model: String) : ChatEvent

    /** A guard rail fired (loop detection); informational, no reply expected. */
    data class Warning(val message: String) : ChatEvent
    data class Failure(val message: String) : ChatEvent
    data class Ignored(val type: String?) : ChatEvent
}

/**
 * Maps Mercury wire events to [ChatEvent].
 *
 * Every type Mercury's web channel broadcasts is decoded, so an interaction the
 * agent is blocked on never disappears silently. Unknown types stay
 * [ChatEvent.Ignored] so a Mercury release adding events cannot crash the client.
 */
object MercuryEventParser {

    val defaultJson: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        // The daemon sends explicit `null` for fields that are "not set" (permissionMode is the
        // one that bit: `{..., "permissionMode": null}`), and a non-nullable field with a default
        // is otherwise a hard parse error the owner sees as a red banner in Réglages. Coercing
        // the null to the default is exactly what the decoder's own error message recommends.
        coerceInputValues = true
    }

    fun parse(raw: RawSseEvent, json: Json = defaultJson): ChatEvent {
        // Keep-alives carry no payload; skip the JSON round-trip.
        if (raw.type == "heartbeat") return ChatEvent.Ignored(raw.type)
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
                options = obj.optionList("options").ifEmpty { optionsOf("yes", "always", "no") },
            )

            "permission_continue" -> ChatEvent.ContinueRequest(
                id = obj.string("id"),
                question = obj.string("question"),
                options = obj.optionList("options").ifEmpty { optionsOf("yes", "no") },
            )

            // No `ifEmpty` fallback here on purpose: the agent wrote these answers, the app cannot
            // guess them. An empty list means the payload was unusable and the reducer drops the
            // prompt rather than drawing a card whose buttons would post an invented value.
            "choice_prompt" -> ChatEvent.ChoicePrompt(
                id = obj.string("id"),
                question = obj.string("question"),
                options = obj.optionList("options"),
            )

            "permission_mode" -> ChatEvent.PermissionModeRequest(
                id = obj.string("id"),
                options = obj.optionList("options").ifEmpty { optionsOf("ask-me", "allow-all") },
            )

            "session_switched" -> ChatEvent.SessionSwitched(
                sessionId = obj.string("id"),
                title = obj.string("title").ifBlank { obj.string("alias") },
            )

            "provider" -> ChatEvent.ProviderInfo(
                name = obj.string("name"),
                model = obj.string("model"),
            )

            "loop_warning" -> ChatEvent.Warning(obj.string("message"))
            "error" -> ChatEvent.Failure(obj.string("message").ifBlank { "Unknown error" })
            else -> ChatEvent.Ignored(raw.type)
        }
    }

    /** Longest list an interaction card can draw without overflowing a phone screen. */
    const val MAX_OPTIONS = 8

    private fun JsonObject.string(key: String): String =
        runCatching { this[key]?.jsonPrimitive?.content ?: "" }.getOrDefault("")

    /**
     * Reads an `options` array in both wire shapes.
     *
     * - `{"value":"a","label":"A"}` — what `presentChoicePrompt` emits today;
     * - `"a"` — the bare string older prompts (and the approval fallbacks) use; label stays null.
     *
     * Entries with a missing or blank `value` are dropped: a button posting `""` is a dead control
     * (the daemon answers `''` and the agent resumes as if the user had chosen nothing). Values are
     * deduplicated — the route keys the answer on the value, so two buttons on the same value are
     * indistinguishable — and the list is capped at [MAX_OPTIONS].
     */
    private fun JsonObject.optionList(key: String): List<ChoiceOption> {
        val elements = this[key] as? JsonArray ?: return emptyList()
        return elements.mapNotNull { element ->
            when (element) {
                is JsonObject -> {
                    val value = element.string("value")
                    if (value.isBlank()) null
                    else ChoiceOption(value, element.string("label").ifBlank { null })
                }
                // JsonNull is a JsonPrimitive too; `contentOrNull` and `isString` both reject it.
                is JsonPrimitive -> element.contentOrNull
                    ?.takeIf { element.isString }
                    ?.let { ChoiceOption(it) }
                else -> null
            }
        }.distinctBy { it.value }.take(MAX_OPTIONS)
    }
}
