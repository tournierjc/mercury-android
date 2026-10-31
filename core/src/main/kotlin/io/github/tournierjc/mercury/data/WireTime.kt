package io.github.tournierjc.mercury.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement

/**
 * The `timestamp` a transcript carries, **presence included** (SPECS/115 §3.4).
 *
 * The three shapes are three different rules, not one missing value:
 *
 * - [Absent] — the key is not there. Every transcript written before the daemon dated them, so
 *   the exchange keeps the clock it always had: the turn's own (`startedAt` + `durationMs`).
 * - [Unreadable] — the key is there and says nothing the app can use (`null`, `""`, `0`,
 *   `"hier"`). The daemon had a date field and did not fill it: the bubble draws no hour line at
 *   all. Borrowing the run's clock here would be the app inventing a time the server refused to
 *   give.
 * - [At] — a real epoch-milliseconds date; the exchange is drawn at that hour.
 *
 * Collapsing the first two into `null` (the shape before this type) is what made the app draw a
 * turn clock under a bubble the daemon had explicitly left undated.
 */
sealed interface WireTime {

    /** The transcript does not carry the field at all: the turn's clock dates the exchange. */
    data object Absent : WireTime

    /** The field is written but yields no date: no hour line. */
    data object Unreadable : WireTime

    /** The exchange's own date, epoch milliseconds. */
    data class At(val millis: Long) : WireTime
}

/**
 * Reads [WireTime] from the wire.
 *
 * The property keeps a default of [WireTime.Absent], and kotlinx uses that default *without
 * calling this serializer* when the key is missing — that is the only way the two undated shapes
 * stay distinguishable. Anything this serializer does see was written by the daemon, so it is
 * either a date or [WireTime.Unreadable]; `null` is never returned, and a caller that receives it
 * (a `null` written through `TimestampSerializer` conventions) must read it as [WireTime.Unreadable]
 * too.
 */
object WireTimeSerializer : KSerializer<WireTime?> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("io.github.tournierjc.mercury.WireTime", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): WireTime? {
        val json = decoder as? JsonDecoder
            ?: return decoder.decodeLong().takeIf { it > 0 }?.let { WireTime.At(it) }
                ?: WireTime.Unreadable
        val element: JsonElement = json.decodeJsonElement()
        val millis = timestampMillisOf(element)
        return if (millis != null && millis > 0) WireTime.At(millis) else WireTime.Unreadable
    }

    override fun serialize(encoder: Encoder, value: WireTime?) {
        when (value) {
            is WireTime.At -> encoder.encodeLong(value.millis)
            else -> encoder.encodeNull()
        }
    }
}
