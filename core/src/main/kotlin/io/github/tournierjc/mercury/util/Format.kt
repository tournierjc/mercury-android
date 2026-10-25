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
