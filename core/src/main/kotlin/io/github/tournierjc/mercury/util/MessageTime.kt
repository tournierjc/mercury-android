package io.github.tournierjc.mercury.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The hour under a message bubble — the one formatter every conversation uses (SPECS/115 §3.3).
 *
 * The shape is the reader's own clock: `14:32` in French, `2:32 PM` in English, minute
 * precision, no seconds, no date — the stored thread's own text carries the day.
 *
 * A message the daemon did not date has no hour at all: this returns `null` rather than
 * inventing a local reception time (§3.4), and the bubble then draws no line.
 */
fun formatMessageTime(
    instant: Instant?,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String? {
    if (instant == null) return null
    return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(zone)
        .format(instant)
}

/**
 * The same hour, read from the wire field itself: `timestamp`, epoch milliseconds (§5 bis).
 *
 * `null` means the daemon did not date the message, and `0` is 1970 — never a message hour. Both
 * yield no hour, so a `0` that slid in through a forgiving default cannot print `01:00`.
 */
fun messageTimeFromMillis(millis: Long?): Instant? =
    millis?.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it) }
