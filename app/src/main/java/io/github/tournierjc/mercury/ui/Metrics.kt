package io.github.tournierjc.mercury.ui

import java.util.Locale
import kotlin.math.abs

/**
 * Numbers as the app's two languages write them.
 *
 * No `NumberFormat`: the values are counts and token totals, and the grouping must be the character
 * the webapp uses — a wide space or a comma-grouped "87,197,529" reads as a different number to a
 * French reader. Integer arithmetic only, so the same value always prints the same way.
 *
 * The locale is a parameter, defaulting to the device's: a French phone gets U+202F and "Md", an
 * English one a comma and "B". Deciding the language from the digits (what this file used to do)
 * showed "1,3 Md" to an English user.
 */

/** French thousands separator, narrow enough never to break across a line. */
private const val NARROW_SPACE = '\u202F'

private fun isFrench(locale: Locale): Boolean = locale.language == "fr"

/** `87197529` → `87 197 529` (fr) / `87,197,529` (en). Counts, not magnitudes. */
fun formatCount(value: Long, locale: Locale = Locale.getDefault()): String {
    val separator = if (isFrench(locale)) NARROW_SPACE.toString() else ","
    val digits = abs(value).toString()
    val grouped = digits.reversed().chunked(3).joinToString(separator).reversed()
    return if (value < 0) "-$grouped" else grouped
}

/**
 * Token counts at the width a phone has: `812`, `42 k`, `1,2 M`, `81,2 M` — `1.2 M`, `81.2 M` in
 * English, where a billion is a B and not a Md.
 *
 * One decimal, dropped when it is zero (`42 k`, not `42,0 k`), rounded half up.
 */
fun formatCompact(value: Long, locale: Locale = Locale.getDefault()): String {
    val magnitude = abs(value)
    val sign = if (value < 0) "-" else ""
    val billion = if (isFrench(locale)) "Md" else "B"
    return when {
        magnitude >= 1_000_000_000L -> scaled(magnitude, 1_000_000_000L, billion, sign, locale)
        magnitude >= 1_000_000L -> scaled(magnitude, 1_000_000L, "M", sign, locale)
        magnitude >= 1_000L -> scaled(magnitude, 1_000L, "k", sign, locale)
        else -> "$sign$magnitude"
    }
}

private fun scaled(magnitude: Long, unit: Long, suffix: String, sign: String, locale: Locale): String {
    // Tenths of the unit, rounded half up without going through a Double.
    val tenths = (magnitude + unit / 20) / (unit / 10)
    val whole = tenths / 10
    val tenth = tenths % 10
    val decimal = if (isFrench(locale)) "," else "."
    val number = if (tenth == 0L) formatCount(whole, locale) else "${formatCount(whole, locale)}$decimal$tenth"
    return "$sign$number $suffix"
}
