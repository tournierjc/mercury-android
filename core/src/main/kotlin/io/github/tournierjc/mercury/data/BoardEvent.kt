package io.github.tournierjc.mercury.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Events of `/api/boards/:id/events` (SSE), the feed that makes a board live.
 *
 * The daemon emits:
 *  - `board-update` on connect and after every change to the board, debounced 800 ms
 *    server-side: the whole normalised board (`{id,name,status,active,cards:[…]}`), so a
 *    frame replaces the card list instead of patching one card;
 *  - `feedback-request` when a running card waits for a decision (it bypasses the
 *    debounce because the owner has to see it at once): the request object
 *    `{id,boardId,cardId,agentId,question,options,createdAt,resolved}`;
 *  - a `: keepalive` comment every 15 s (dropped by [SseParser]).
 */
sealed interface BoardEvent {

    /** A whole board, cards included: the frame carries the state, not a diff. */
    data class Update(val board: JsonObject, val cards: List<JsonObject>) : BoardEvent

    /** A card needs a decision before it can continue. */
    data class FeedbackRequest(val request: JsonObject) : BoardEvent

    data class Unknown(val type: String) : BoardEvent

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parse(type: String?, data: String): BoardEvent {
            val payload = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull()
                ?: return Unknown(type.orEmpty())
            return when (type) {
                "board-update" -> Update(payload, BoardJson.cards(payload))
                "feedback-request" -> FeedbackRequest(payload)
                else -> Unknown(type.orEmpty())
            }
        }
    }
}
