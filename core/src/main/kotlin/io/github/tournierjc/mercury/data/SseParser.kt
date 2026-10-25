package io.github.tournierjc.mercury.data

/**
 * A single event from Mercury's `/api/chat/events` server-sent-event feed.
 *
 * The wire format is the standard SSE framing used by the Mercury web channel:
 *
 * ```
 * event: text_delta
 * data: {"text":"Hel","targetId":"sess-1"}
 * ```
 *
 * Framing rules implemented here (and covered by unit tests):
 *  - `\n`, `\r\n` and `\r` are all accepted as line terminators.
 *  - a blank line dispatches the buffered event;
 *  - lines starting with `:` are comments (Mercury's keepalive) and are dropped;
 *  - several `data:` lines are joined with `\n`;
 *  - chunks may split anywhere, including inside a line.
 */
class SseParser {

    private val lineBuffer = StringBuilder()
    private val dataBuffer = StringBuilder()
    private var eventType: String? = null
    private var pendingCarriageReturn = false

    fun feed(chunk: String): List<RawSseEvent> {
        val events = ArrayList<RawSseEvent>()
        for (char in chunk) {
            if (pendingCarriageReturn) {
                pendingCarriageReturn = false
                if (char == '\n') continue // already handled as CRLF terminator
            }
            when (char) {
                '\r' -> {
                    flushLine(events)
                    pendingCarriageReturn = true
                }
                '\n' -> flushLine(events)
                else -> lineBuffer.append(char)
            }
        }
        return events
    }

    fun reset() {
        lineBuffer.setLength(0)
        dataBuffer.setLength(0)
        eventType = null
        pendingCarriageReturn = false
    }

    private fun flushLine(events: MutableList<RawSseEvent>) {
        val line = lineBuffer.toString()
        lineBuffer.setLength(0)
        when {
            line.isEmpty() -> dispatch(events)
            line.startsWith(":") -> Unit
            line.startsWith("event:") -> eventType = line.substring("event:".length).trim()
            line.startsWith("data:") -> {
                if (dataBuffer.isNotEmpty()) dataBuffer.append('\n')
                dataBuffer.append(line.substring("data:".length).trimStart())
            }
            else -> Unit // unknown field, ignored per the SSE spec
        }
    }

    private fun dispatch(events: MutableList<RawSseEvent>) {
        val type = eventType
        val data = dataBuffer.toString()
        eventType = null
        dataBuffer.setLength(0)
        if (type == null && data.isEmpty()) return
        events += RawSseEvent(type = type, data = data)
    }
}
