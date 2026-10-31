package io.github.tournierjc.mercury.util

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The hour under a bubble (SPECS/115 §3.3): the reader's own clock, and no line at all
 * when the daemon did not date the message (§4.4).
 *
 * Every expectation is an exact string: "not null" would pass for a formatter that prints
 * the wrong hour, which is the failure this file exists to catch.
 */
class MessageTimeTest {

    private val paris = ZoneId.of("Europe/Paris")

    @Test
    fun `the hour is the reader's wall clock, not UTC`() {
        // 13:32 UTC is 14:32 in Paris on that date (CET, +01:00 — DST ended on 25 October).
        val instant = Instant.parse("2026-10-29T13:32:00Z")
        assertEquals("14:32", formatMessageTime(instant, paris, Locale.FRANCE))
        // CLDR puts a narrow no-break space before AM/PM; normalise it, the hour is the point.
        val english = formatMessageTime(instant, ZoneId.of("UTC"), Locale.US)!!
            .replace('\u202F', ' ')
            .replace('\u00A0', ' ')
        assertEquals("2:32 PM", english)
    }

    @Test
    fun `midnight is 00 00 in French and 12 00 AM in English, not 24 00`() {
        // 23:00 UTC is 00:00 the next day in Paris (CET) — the hour that breaks naive formats.
        val midnight = Instant.parse("2026-10-28T23:00:00Z")
        assertEquals("00:00", formatMessageTime(midnight, paris, Locale.FRANCE))
        val english = formatMessageTime(midnight, paris, Locale.US)!!
            .replace('\u202F', ' ')
            .replace('\u00A0', ' ')
        assertEquals("12:00 AM", english)
    }

    @Test
    fun `midday is 12 00, not 00 00`() {
        // 11:00 UTC is 12:00 in Paris (CET) — the hour a 12-hour format must keep as 12 PM.
        val noon = Instant.parse("2026-10-29T11:00:00Z")
        assertEquals("12:00", formatMessageTime(noon, paris, Locale.FRANCE))
        val english = formatMessageTime(noon, paris, Locale.US)!!
            .replace('\u202F', ' ')
            .replace('\u00A0', ' ')
        assertEquals("12:00 PM", english)
    }

    @Test
    fun `a message the daemon did not date draws no line`() {
        // §4.4: an absent or null `timestamp` is not 1970 and not "just now" — it is no hour.
        assertNull(formatMessageTime(null, paris, Locale.FRANCE))
        assertNull(formatMessageTime(null, paris, Locale.US))
        assertNull(messageTimeFromMillis(null))
        assertNull(messageTimeFromMillis(0L))
    }

    @Test
    fun `bot journal millis are epoch ms, and zero means undated`() {
        assertEquals(
            Instant.parse("2026-10-29T13:32:00Z"),
            messageTimeFromMillis(1_793_280_720_000L),
        )
        assertNull(messageTimeFromMillis(0L))
        assertEquals(
            "14:32",
            formatMessageTime(messageTimeFromMillis(1_793_280_720_000L), paris, Locale.FRANCE),
        )
    }
}
