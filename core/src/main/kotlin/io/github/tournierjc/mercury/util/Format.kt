package io.github.tournierjc.mercury.util

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

/** Token counts as the dashboard shows them: `12,2k`, `1,4M`. */
fun formatTokens(value: Long): String {
    val abs = if (value < 0) -value else value
    val sign = if (value < 0) "-" else ""
    return when {
        abs >= 1_000_000 -> "$sign${oneDecimal(abs / 1_000_000.0)}M"
        abs >= 1_000 -> "$sign${oneDecimal(abs / 1_000.0)}k"
        else -> "$sign$abs"
    }
}

private fun oneDecimal(value: Double): String {
    val rounded = (value * 10).toLong()
    val whole = rounded / 10
    val tenth = rounded % 10
    return if (tenth == 0L) "$whole" else "$whole,$tenth"
}

/**
 * Disk sizes as the console prints them: `512 o`, `2,0 ko`, `5,0 Mo`, `1,5 Go`.
 * 0 means "nothing measured" here — an empty folder reads better than `0 o`.
 */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "—"
    val units = listOf("o", "ko", "Mo", "Go", "To")
    if (bytes < 1024) return "$bytes o"
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    // Always one decimal for a scaled unit: `2,0 ko` reads as a size, `2 ko` as a count.
    val tenths = (value * 10).toLong()
    return "${tenths / 10},${tenths % 10} ${units[index]}"
}

/**
 * A dollar estimate as the board prints it: `$0,47`, `$12`, `<$0,001`.
 *
 * A cost that is not zero but rounds to zero must not print as zero: `$0,000` reads as free, so
 * anything under a tenth of a cent says so. Whole dollars lose their cents (`$12`), and the decimal
 * separator is the app's comma, unlike the web app's dot.
 */
fun formatCost(usd: Double): String {
    if (usd <= 0.0) return "$0"
    if (usd < 0.001) return "<$0,001"
    if (usd < 1.0) {
        // Three decimals, then the trailing zeros that carry nothing: `$0,47`, not `$0,470`.
        val thousandths = (usd * 1000).toLong()
        val fraction = (thousandths % 1000).toString().padStart(3, '0').trimEnd('0')
        return "$${thousandths / 1000},$fraction"
    }
    val cents = (usd * 100).toLong()
    val whole = cents / 100
    val fraction = (cents % 100).toString().padStart(2, '0').trimEnd('0')
    return if (fraction.isEmpty()) "$$whole" else "$$whole,$fraction"
}
