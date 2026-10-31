package io.github.tournierjc.mercury.data

import io.github.tournierjc.mercury.util.messageTimeFromMillis
import java.time.Instant

/**
 * A thread's stored exchange, as the chat list draws it.
 *
 * The daemon dates every stored message with `timestamp` — epoch milliseconds, [TimestampSerializer]
 * reads the integer of SPECS/115 §5 bis and the ISO string `chat.ts` `toThread` still sends — and
 * that date is the only honest source for the hour under the bubble (§3.4). A message the route
 * left undated keeps `at = null` and draws no hour. Messages reduced from the live SSE stream carry
 * no date of their own; the reply of a finished turn gets one later, still from the server, through
 * [ChatReducer.stampAssistantAt] — see [lastReplyTime].
 */
fun historyMessages(messages: List<MessageDto>): List<ChatMessage> =
    messages.mapIndexed { index, dto ->
        ChatMessage(
            id = "history-$index",
            role = if (dto.role == "user") ChatMessage.Role.User else ChatMessage.Role.Assistant,
            // The daemon stored it, so it travelled: a read-back bubble never shows an unsent line.
            delivery = ChatMessage.Delivery.Sent,
            text = dto.content,
            at = messageTimeFromMillis(dto.timestamp),
        )
    }

/**
 * The server's date for the reply that has just been written, read back off the stored thread.
 *
 * The daemon writes the turn when it ends, so the reply is the thread's last message. A last
 * message still from the owner means the answer is not stored yet: there is no date to take and
 * the caller may look again — it must not take the *previous* reply's date. An absent or
 * unreadable `timestamp` gives `null`, and the bubble then draws no hour (§3.4) rather than
 * borrow the phone's clock.
 */
fun List<MessageDto>.lastReplyTime(): Instant? =
    lastOrNull()
        ?.takeIf { it.role != "user" }
        ?.let { messageTimeFromMillis(it.timestamp) }
