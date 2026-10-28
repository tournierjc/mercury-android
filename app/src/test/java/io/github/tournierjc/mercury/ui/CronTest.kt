package io.github.tournierjc.mercury.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The cadence the schedules screen prints above each row. */
class CronTest {

    @Test
    fun `the daily shapes are read`() {
        assertEquals(CronCadence.Daily(3, 0), parseCron("0 3 * * *"))
        assertEquals(CronCadence.Daily(23, 30), parseCron("30 23 * * *"))
        assertEquals(CronCadence.Weekdays(9, 15), parseCron("15 9 * * 1-5"))
        assertEquals(CronCadence.Weekly(0, 4, 0), parseCron("0 4 * * 0"))
        assertEquals(CronCadence.Monthly(1, 2, 0), parseCron("0 2 1 * *"))
    }

    @Test
    fun `the repeating shapes are read`() {
        assertEquals(CronCadence.EveryMinutes(15), parseCron("*/15 * * * *"))
        assertEquals(CronCadence.Hourly, parseCron("0 * * * *"))
        assertEquals(CronCadence.EveryHours(6), parseCron("0 */6 * * *"))
    }

    @Test
    fun `an expression we do not read is not guessed at`() {
        assertNull(parseCron("0 3 * 6 *"))        // one month of the year
        assertNull(parseCron("0 3 1-15 * *"))     // a day range
        assertNull(parseCron("0 0 3 * * *"))      // six fields: seconds, not ours
        assertNull(parseCron("not a cron"))
        assertNull(parseCron(""))
        assertNull(parseCron(null))
    }
}
