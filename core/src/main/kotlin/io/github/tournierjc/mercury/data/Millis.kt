package io.github.tournierjc.mercury.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive

/**
 * A millisecond timestamp, read from whatever JSON number the daemon sends.
 *
 * `mtimeMs` comes back as `1793106466172.9038` — a float, because it is a filesystem
 * `stat().mtimeMs` — while other timestamps are integral. Decoding that into a `Long` throws,
 * and every screen that read a list behind a `runCatching` showed "empty" instead of "broken":
 * a bot with deliverables looked like a bot with none. Accepting both shapes (and a numeric
 * string, which older daemons sent) removes that class of silent blank screen.
 */
object MillisSerializer : KSerializer<Long> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("MercuryMillis", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Long {
        val json = decoder as? JsonDecoder ?: return decoder.decodeLong()
        val raw = json.decodeJsonElement().jsonPrimitive.content
        return raw.toDoubleOrNull()?.toLong() ?: 0L
    }

    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
}
