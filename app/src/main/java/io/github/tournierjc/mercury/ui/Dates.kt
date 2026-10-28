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
private const val DAY_AND_TIME = "d MMMM HH:mm"

/**
 * `2026-10-29T03:00:00.000Z` → `29 octobre 03:00` in the device's zone, or null if unreadable.
 *
 * The month's name comes from the reader's language, taken from the default locale — which
 * [io.github.tournierjc.mercury.device.LocaleStore] sets when a language is chosen. The pattern used
 * to be picked by a `if (language == "fr") … else English`, so every language added after French
 * would silently have read English: the shape is the same in most languages, the words are not.
 */
fun formatWhen(iso: String?, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String? {
    val text = iso?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return runCatching {
        Instant.parse(text).atZone(zone).format(DateTimeFormatter.ofPattern(DAY_AND_TIME, locale))
    }.getOrNull()
}

/** `1793148728902` → `28/10/2026 14:52`, the shape the web app prints, in the device's zone. */
private val dayThenTime = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

fun formatStamp(ms: Long?, zone: ZoneId = ZoneId.systemDefault()): String? {
    val stamp = ms?.takeIf { it > 0 } ?: return null
    return runCatching { Instant.ofEpochMilli(stamp).atZone(zone).format(dayThenTime) }.getOrNull()
}
