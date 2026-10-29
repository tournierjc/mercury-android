package io.github.tournierjc.mercury.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'âge affiché sous une bulle, et la lecture de l'horodatage du démon.
 *
 * Les bornes sont ce qui compte : une minute pile n'est plus « à l'instant », une heure pile n'est
 * plus en minutes, et un horodatage absent doit rester absent plutôt que d'inventer « à l'instant »
 * sur un message dont on ne sait rien.
 */
class RelativeAgeTest {

    private val now = 1_800_000_000_000L

    @Test
    fun `under a minute is just now`() {
        assertEquals(RelativeAge.JustNow, relativeAge(now, now))
        assertEquals(RelativeAge.JustNow, relativeAge(now, now - 59_000))
    }

    @Test
    fun `minutes up to an hour`() {
        assertEquals(RelativeAge.Minutes(1), relativeAge(now, now - 60_000))
        assertEquals(RelativeAge.Minutes(3), relativeAge(now, now - 3 * 60_000 - 30_000))
        assertEquals(RelativeAge.Minutes(59), relativeAge(now, now - 59 * 60_000))
    }

    @Test
    fun `hours up to a day`() {
        assertEquals(RelativeAge.Hours(1), relativeAge(now, now - 60 * 60_000))
        assertEquals(RelativeAge.Hours(23), relativeAge(now, now - 23 * 60 * 60_000))
    }

    @Test
    fun `days beyond that`() {
        assertEquals(RelativeAge.Days(1), relativeAge(now, now - 24 * 60 * 60_000))
        assertEquals(RelativeAge.Days(9), relativeAge(now, now - 9 * 24 * 60 * 60_000))
    }

    @Test
    fun `no time means no age`() {
        assertNull(relativeAge(now, 0))
    }

    @Test
    fun `a clock ahead of the phone reads as just now, not as a negative age`() {
        assertEquals(RelativeAge.JustNow, relativeAge(now, now + 120_000))
    }

    @Test
    fun `the daemon timestamp is read with or without an offset`() {
        assertEquals(1_800_000_000_000L, epochMillis("2027-01-15T08:00:00Z"))
        assertEquals(1_800_000_000_000L, epochMillis("2027-01-15T09:00:00+01:00"))
        assertEquals(1_800_000_000_512L, epochMillis("2027-01-15T08:00:00.512Z"))
    }

    @Test
    fun `an unreadable timestamp is zero, never a made-up date`() {
        assertEquals(0L, epochMillis(""))
        assertEquals(0L, epochMillis("hier"))
        assertTrue(epochMillis("2027-13-45T99:99:99Z") == 0L)
    }
}
