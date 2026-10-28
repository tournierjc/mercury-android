package io.github.tournierjc.mercury.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.tournierjc.mercury.R

/**
 * A cron expression, read as a cadence.
 *
 * The schedules screen used the raw expression as its accent *kicker*, so the eye landed on
 * `0 3 * * *` — the least readable thing on the row, and the one that says the least. The cadence
 * goes on top now and the expression stays in the meta beside it.
 *
 * Only the shapes a person sets on purpose are recognised; anything else returns null and the caller
 * shows the expression, which beats a confident wrong sentence.
 */
sealed interface CronCadence {
    data class EveryMinutes(val minutes: Int) : CronCadence
    data object Hourly : CronCadence
    data class EveryHours(val hours: Int) : CronCadence
    data class Daily(val hour: Int, val minute: Int) : CronCadence
    data class Weekdays(val hour: Int, val minute: Int) : CronCadence
    data class Weekly(val day: Int, val hour: Int, val minute: Int) : CronCadence
    data class Monthly(val day: Int, val hour: Int, val minute: Int) : CronCadence
}

/** Five-field cron only — the daemon stores five, and a six-field one is not ours to interpret. */
fun parseCron(cron: String?): CronCadence? {
    val fields = cron?.trim()?.split(Regex("\\s+")) ?: return null
    if (fields.size != 5) return null
    val (minute, hour, dayOfMonth, month, dayOfWeek) = fields
    val minutes = minute.toIntOrNull()
    val hours = hour.toIntOrNull()

    fun at(): Pair<Int, Int>? = if (minutes != null && hours != null) hours to minutes else null

    return when {
        minute.startsWith("*/") && hour == "*" && dayOfMonth == "*" && month == "*" && dayOfWeek == "*" ->
            minute.removePrefix("*/").toIntOrNull()?.let { CronCadence.EveryMinutes(it) }
        minute == "0" && hour == "*" && dayOfMonth == "*" && month == "*" && dayOfWeek == "*" ->
            CronCadence.Hourly
        minute == "0" && hour.startsWith("*/") && dayOfMonth == "*" && month == "*" && dayOfWeek == "*" ->
            hour.removePrefix("*/").toIntOrNull()?.let { CronCadence.EveryHours(it) }
        dayOfMonth == "*" && month == "*" && dayOfWeek == "*" ->
            at()?.let { (h, m) -> CronCadence.Daily(h, m) }
        dayOfMonth == "*" && month == "*" && dayOfWeek == "1-5" ->
            at()?.let { (h, m) -> CronCadence.Weekdays(h, m) }
        dayOfMonth == "*" && month == "*" && dayOfWeek.toIntOrNull() in 0..6 ->
            at()?.let { (h, m) -> CronCadence.Weekly(dayOfWeek.toInt(), h, m) }
        dayOfMonth.toIntOrNull() in 1..31 && month == "*" && dayOfWeek == "*" ->
            at()?.let { (h, m) -> CronCadence.Monthly(dayOfMonth.toInt(), h, m) }
        else -> null
    }
}

/** The cadence in the owner's language, or null when the expression is not one we read. */
@Composable
fun cronCadenceLabel(cron: String?): String? {
    val day = dayNameArray()
    return when (val cadence = parseCron(cron)) {
        is CronCadence.EveryMinutes -> stringResource(R.string.cron_every_minutes, cadence.minutes)
        CronCadence.Hourly -> stringResource(R.string.cron_hourly)
        is CronCadence.EveryHours -> stringResource(R.string.cron_every_hours, cadence.hours)
        is CronCadence.Daily -> stringResource(R.string.cron_daily, clock(cadence.hour, cadence.minute))
        is CronCadence.Weekdays -> stringResource(R.string.cron_weekdays, clock(cadence.hour, cadence.minute))
        is CronCadence.Weekly -> stringResource(
            R.string.cron_weekly,
            day[cadence.day],
            clock(cadence.hour, cadence.minute),
        )
        is CronCadence.Monthly -> stringResource(R.string.cron_monthly, cadence.day, clock(cadence.hour, cadence.minute))
        null -> null
    }
}

private fun clock(hour: Int, minute: Int): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

@Composable
private fun dayNameArray(): List<String> = listOf(
    stringResource(R.string.cron_day_sunday),
    stringResource(R.string.cron_day_monday),
    stringResource(R.string.cron_day_tuesday),
    stringResource(R.string.cron_day_wednesday),
    stringResource(R.string.cron_day_thursday),
    stringResource(R.string.cron_day_friday),
    stringResource(R.string.cron_day_saturday),
)
