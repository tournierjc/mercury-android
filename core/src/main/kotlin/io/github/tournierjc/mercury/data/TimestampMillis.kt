package io.github.tournierjc.mercury.data

import java.time.OffsetDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * The `timestamp` of a conversation message: epoch milliseconds (SPECS/115 §5 bis).
 *
 * JC's contract serves an integer — `1793280720000`, the same shape as `ChatMessage.timestamp`
 * in `chat-history.ts` — on every message of a conversation, and calls it optional. Two shapes
 * are therefore read, and only those two:
 *
 * - a JSON number: the contract (`/api/chat/history`, and the bot conversation once the daemon
 *   dates its messages);
 * - an ISO-8601 string: what `chat.ts` `toThread` sends today on `GET /api/chat/threads/:id`,
 *   because it rewrites the stored date with `toISOString()`. Reading it here keeps the Mercury
 *   conversation working while the daemon drops that server-side formatting.
 *
 * Anything else — an absent key, an explicit `null`, `""`, `"hier"` — decodes to `null`, and the
 * bubble then draws no hour at all (§4.4). It never becomes 1970 and never the phone's clock.
 */
object TimestampSerializer : KSerializer<Long?> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("MercuryTimestamp", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Long? {
        val json = decoder as? JsonDecoder ?: return decoder.decodeLong()
        return timestampMillisOf(json.decodeJsonElement())
    }

    override fun serialize(encoder: Encoder, value: Long?) {
        if (value == null) encoder.encodeNull() else encoder.encodeLong(value)
    }
}

/**
 * The epoch milliseconds an already-decoded `timestamp` element means, or `null` when it means
 * nothing readable (absent, `null`, `""`, `"hier"`, `0`).
 *
 * Shared by [TimestampSerializer] — which cannot tell an absent key from a written `null` — and
 * [WireTimeSerializer], which can and must ([WireTime]).
 */
fun timestampMillisOf(element: JsonElement): Long? {
    if (element is JsonNull) return null
    val primitive = element as? JsonPrimitive ?: return null
    if (!primitive.isString) return primitive.content.toDoubleOrNull()?.toLong()
    val text = primitive.content.trim()
    if (text.isEmpty()) return null
    return text.toLongOrNull()
        ?: runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
}
