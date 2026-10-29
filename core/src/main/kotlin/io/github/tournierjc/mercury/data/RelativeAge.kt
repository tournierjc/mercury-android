package io.github.tournierjc.mercury.data

import java.time.Instant
import java.time.OffsetDateTime

/**
 * How long ago a turn happened, in the units a phone shows.
 *
 * The words live in the app's resources: core has no idea whether the owner reads « il y a 3 min »
 * or "3m ago", and a notification that invented its own language would be the one screen in the
 * app that ignores the setting.
 */
sealed interface RelativeAge {
    data object JustNow : RelativeAge
    data class Minutes(val count: Int) : RelativeAge
    data class Hours(val count: Int) : RelativeAge
    data class Days(val count: Int) : RelativeAge
}

/**
 * The age of [atMs] at [nowMs], or null when the time is unknown (0: an older daemon, or a message
 * the app built before it had a clock). A timestamp in the future — a server whose clock runs
 * ahead — reads as « à l'instant » rather than as a negative age.
 */
fun relativeAge(nowMs: Long, atMs: Long): RelativeAge? {
    if (atMs <= 0) return null
    val elapsed = (nowMs - atMs).coerceAtLeast(0)
    return when {
        elapsed < 60_000L -> RelativeAge.JustNow
        elapsed < 3_600_000L -> RelativeAge.Minutes((elapsed / 60_000L).toInt())
        elapsed < 86_400_000L -> RelativeAge.Hours((elapsed / 3_600_000L).toInt())
        else -> RelativeAge.Days((elapsed / 86_400_000L).toInt())
    }
}

/**
 * The daemon's per-message timestamp (`2026-10-29T08:12:44.512Z`, sometimes without the offset) as
 * epoch milliseconds; 0 when it cannot be read, which the UI treats as « no time to show ».
 */
fun epochMillis(iso: String): Long {
    if (iso.isBlank()) return 0
    return runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }
        .recoverCatching { Instant.parse(iso).toEpochMilli() }
        .getOrDefault(0)
}
