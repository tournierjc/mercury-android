package io.github.tournierjc.mercury.util

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The one place a number becomes text: counts, token totals, disk sizes and costs.
 *
 * Every screen used to carry its own formatter (a French-only one here, a locale-aware one in the
 * app, a third in the workspace browser), so the same token count could print `1,2 M` and `1.2 M`
 * on two screens. They all live here now, and every function takes the locale it prints for —
 * defaulting to the device's, so a French phone gets U+202F and a comma decimal, an English one a
 * comma grouping and a dot. Integer arithmetic wherever the value allows it, so the same value
 * always prints the same way.
 *
 * Rounding, not truncation, everywhere: `1,25 M` must not lie low as `1,2 M` while a quota reads
 * the same digits.
 */

/** French thousands separator, narrow enough never to break across a line. */
private const val NARROW_SPACE = '\u202F'

private fun isFrench(locale: Locale): Boolean = locale.language == "fr"

private fun decimal(locale: Locale): String = if (isFrench(locale)) "," else "."

/** Uptime in the compact form a phone header can afford: `3 j 04 h`, `12 min`, `45 s`. */
fun formatUptime(seconds: Long): String {
    if (seconds <= 0) return "—"
    val days = seconds / 86_400
    val hours = (seconds % 86_400) / 3_600
    val minutes = (seconds % 3_600) / 60
    return when {
        days > 0 -> "$days j ${hours.toString().padStart(2, '0')} h"
        hours > 0 -> "$hours h ${minutes.toString().padStart(2, '0')} min"
        minutes > 0 -> "$minutes min"
        else -> "$seconds s"
    }
}

/** `87197529` → `87 197 529` (fr) / `87,197,529` (en). Counts, not magnitudes. */
fun formatCount(value: Long, locale: Locale = Locale.getDefault()): String {
    val separator = if (isFrench(locale)) NARROW_SPACE.toString() else ","
    val digits = abs(value).toString()
    val grouped = digits.reversed().chunked(3).joinToString(separator).reversed()
    return if (value < 0) "-$grouped" else grouped
}

/**
 * Token counts at the width a phone has: `812`, `42 k`, `1,2 M`, `81,2 M` — `1.2 M` in English,
 * where a billion is a B and not a Md.
 *
 * One decimal, rounded half up, dropped when it is zero (`42 k`, not `42,0 k`).
 */
fun formatTokens(value: Long, locale: Locale = Locale.getDefault()): String {
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
    val number = if (tenth == 0L) formatCount(whole, locale) else "${formatCount(whole, locale)}${decimal(locale)}$tenth"
    return "$sign$number $suffix"
}

/**
 * Disk sizes a phone can read: `512 o`, `1,5 ko`, `3,2 Mo` in French; `512 B`, `1.5 kB`,
 * `3.2 MB` elsewhere. The unit is a word (octet, kilo-octet…), so it follows the locale.
 *
 * A useless decimal is dropped (`2 ko`, not `2,0 ko`), like every other formatter here. 0 or less
 * means "nothing measured" — an empty folder reads better than `0 o`.
 */
fun formatBytes(bytes: Long, locale: Locale = Locale.getDefault()): String {
    if (bytes <= 0) return "—"
    val units = if (isFrench(locale)) listOf("o", "ko", "Mo", "Go", "To") else listOf("B", "kB", "MB", "GB", "TB")
    if (bytes < 1024) return "$bytes ${units[0]}"
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    // Rounding can push a value across its unit (`1023,9 ko` → `1024 ko`): step up while the
    // rounded form has no room left in the current unit.
    var tenths = (value * 10).roundToLong()
    while (tenths >= 10240 && index < units.lastIndex) {
        value /= 1024.0
        index++
        tenths = (value * 10).roundToLong()
    }
    val whole = tenths / 10
    val tenth = tenths % 10
    return if (tenth == 0L) "$whole ${units[index]}" else "$whole${decimal(locale)}$tenth ${units[index]}"
}

/**
 * A dollar estimate as the board prints it: `$0,47`, `$12`, `<$0,001`.
 *
 * A cost that is not zero but rounds to zero must not print as zero: `$0,000` reads as free, so
 * anything under a tenth of a cent says so. Whole dollars lose their cents (`$12`), and the
 * decimal separator follows the locale — the app's comma in French, unlike the web app's dot.
 */
fun formatCost(usd: Double, locale: Locale = Locale.getDefault()): String {
    if (usd <= 0.0) return "$0"
    val dot = decimal(locale)
    if (usd < 0.001) return "<$0${dot}001"
    if (usd < 1.0) {
        // Three decimals, then the trailing zeros that carry nothing: `$0,47`, not `$0,470`.
        val thousandths = (usd * 1000).roundToLong()
        val whole = thousandths / 1000
        val fraction = (thousandths % 1000).toString().padStart(3, '0').trimEnd('0')
        return if (fraction.isEmpty()) "$$whole" else "$$whole$dot$fraction"
    }
    val cents = (usd * 100).roundToLong()
    val whole = cents / 100
    val fraction = (cents % 100).toString().padStart(2, '0').trimEnd('0')
    return if (fraction.isEmpty()) "$$whole" else "$$whole$dot$fraction"
}
