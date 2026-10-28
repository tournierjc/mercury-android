package io.github.tournierjc.mercury.ui

import kotlin.math.abs

/**
 * Numbers as a French phone writes them.
 *
 * Deliberately no `Locale` and no `NumberFormat`: the app ships two languages, the values are
 * counts and token totals, and the separator must be the same narrow no-break space (U+202F)
 * the webapp uses — a wide space or a comma-grouped "87,197,529" reads as a different number.
 * Integer arithmetic only, so `formatCompact(1_250_000)` is "1,3 M" on every device and in the
 * unit test that guards it.
 */

/** Narrow no-break space: the French thousands separator, never broken across a line. */
private const val GROUP_SEPARATOR = '\u202F'

/** `87197529` → `87 197 529`. Counts, not magnitudes: no k/M shortening here. */
fun formatCount(value: Long): String {
    val digits = abs(value).toString()
    val grouped = digits.reversed().chunked(3).joinToString(GROUP_SEPARATOR.toString()).reversed()
    return if (value < 0) "-$grouped" else grouped
}

/**
 * Token counts at the width a phone has: `812`, `42 k`, `1,2 M`, `81,2 M`.
 *
 * One decimal, dropped when it is zero (`42 k`, not `42,0 k`), rounded half up.
 */
fun formatCompact(value: Long): String {
    val magnitude = abs(value)
    val sign = if (value < 0) "-" else ""
    return when {
        magnitude >= 1_000_000_000L -> scaled(magnitude, 1_000_000_000L, "Md", sign)
        magnitude >= 1_000_000L -> scaled(magnitude, 1_000_000L, "M", sign)
        magnitude >= 1_000L -> scaled(magnitude, 1_000L, "k", sign)
        else -> "$sign$magnitude"
    }
}

private fun scaled(magnitude: Long, unit: Long, suffix: String, sign: String): String {
    // Tenths of the unit, rounded half up without going through a Double.
    val tenths = (magnitude + unit / 20) / (unit / 10)
    val whole = tenths / 10
    val tenth = tenths % 10
    val number = if (tenth == 0L) formatCount(whole) else "${formatCount(whole)},$tenth"
    return "$sign$number $suffix"
}
