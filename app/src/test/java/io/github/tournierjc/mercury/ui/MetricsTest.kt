package io.github.tournierjc.mercury.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The numbers on the Status and Configure screens.
 *
 * Two things cannot be left to a screenshot: the thousands separator is a narrow no-break space
 * (U+202F, not a plain space) and the compact form drops a useless decimal. Both are pure
 * functions, so they are checked here instead of on a phone.
 */
class MetricsTest {

    private val nbsp = '\u202F'

    @Test
    fun `counts are grouped with a narrow no-break space`() {
        assertEquals("87${nbsp}197${nbsp}529", formatCount(87_197_529))
        assertEquals("1${nbsp}000", formatCount(1_000))
        assertEquals("999", formatCount(999))
        assertEquals("0", formatCount(0))
    }

    @Test
    fun `a negative count keeps its sign, outside the grouping`() {
        assertEquals("-42${nbsp}000", formatCount(-42_000))
    }

    @Test
    fun `the separator is the narrow one, never a plain space`() {
        val grouped = formatCount(1_000_000)
        assertEquals(2, grouped.count { it == nbsp })
        assertEquals(0, grouped.count { it == ' ' })
    }

    @Test
    fun `tokens are shortened to k and M with a comma decimal`() {
        assertEquals("812", formatCompact(812))
        assertEquals("42 k", formatCompact(42_000))
        assertEquals("42,5 k", formatCompact(42_500))
        assertEquals("1,2 M", formatCompact(1_200_000))
        assertEquals("81,2 M", formatCompact(81_180_986))
    }

    @Test
    fun `a whole magnitude loses its decimal`() {
        assertEquals("1 M", formatCompact(1_000_000))
        assertEquals("80 M", formatCompact(80_000_000))
        assertEquals("3 k", formatCompact(3_000))
    }

    @Test
    fun `rounding is half up, not truncation`() {
        // 1 249 999 truncates to "1,2 M" and rounds to "1,2 M"; 1 250 000 must round up.
        assertEquals("1,2 M", formatCompact(1_249_999))
        assertEquals("1,3 M", formatCompact(1_250_000))
        assertEquals("1,1 M", formatCompact(1_050_000))
    }
}
