package io.github.tournierjc.mercury.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A server timestamp, as a person reads it.
 *
 * The schedules screen printed the raw ISO string the API sent — `2026-10-29T03:00:00.000Z` — for
 * both "next run" and "created", which is the one format that answers neither question: not what
 * time it will be for the reader, not how soon. This says both.
 */
private val dayAndTime = DateTimeFormatter.ofPattern("d MMMM HH:mm", Locale.FRENCH)

/** `2026-10-29T03:00:00.000Z` → `29 octobre 03:00` in the device's zone, or null if unreadable. */
fun formatWhen(iso: String?, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.FRENCH): String? {
    val text = iso?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return runCatching {
        val instant = Instant.parse(text)
        val formatter = if (locale.language == "fr") {
            dayAndTime.withLocale(Locale.FRENCH)
        } else {
            DateTimeFormatter.ofPattern("d MMMM HH:mm", Locale.ENGLISH)
        }
        instant.atZone(zone).format(formatter)
    }.getOrNull()
}
