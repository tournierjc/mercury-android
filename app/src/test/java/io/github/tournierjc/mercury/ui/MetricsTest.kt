package io.github.tournierjc.mercury.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * The numbers on the Status and Configure screens.
 *
 * Two things cannot be left to a screenshot: the thousands separator is a narrow no-break space
 * (U+202F, not a plain space) and the compact form drops a useless decimal. Both are pure
 * functions, so they are checked here instead of on a phone.
 */
class MetricsTest {

    private val nbsp = '\u202F'

    /** The tests that talk about the French form say so, instead of trusting the machine's locale. */
    private val fr = Locale.FRANCE

    @Test
    fun `counts are grouped with a narrow no-break space`() {
        assertEquals("87${nbsp}197${nbsp}529", formatCount(87_197_529, fr))
        assertEquals("1${nbsp}000", formatCount(1_000, fr))
        assertEquals("999", formatCount(999, fr))
        assertEquals("0", formatCount(0, fr))
    }

    @Test
    fun `a negative count keeps its sign, outside the grouping`() {
        assertEquals("-42${nbsp}000", formatCount(-42_000, fr))
    }

    @Test
    fun `the separator is the narrow one, never a plain space`() {
        val grouped = formatCount(1_000_000, fr)
        assertEquals(2, grouped.count { it == nbsp })
        assertEquals(0, grouped.count { it == ' ' })
    }

    @Test
    fun `tokens are shortened to k and M with a comma decimal`() {
        assertEquals("812", formatCompact(812, fr))
        assertEquals("42 k", formatCompact(42_000, fr))
        assertEquals("42,5 k", formatCompact(42_500, fr))
        assertEquals("1,2 M", formatCompact(1_200_000, fr))
        assertEquals("81,2 M", formatCompact(81_180_986, fr))
    }

    @Test
    fun `a whole magnitude loses its decimal`() {
        assertEquals("1 M", formatCompact(1_000_000, fr))
        assertEquals("80 M", formatCompact(80_000_000, fr))
        assertEquals("3 k", formatCompact(3_000, fr))
    }

    @Test
    fun `rounding is half up, not truncation`() {
        // 1 249 999 truncates to "1,2 M" and rounds to "1,2 M"; 1 250 000 must round up.
        assertEquals("1,2 M", formatCompact(1_249_999, fr))
        assertEquals("1,3 M", formatCompact(1_250_000, fr))
        assertEquals("1,1 M", formatCompact(1_050_000, fr))
    }

    @Test
    fun `English gets its own separators and its own billion`() {
        val en = Locale.US
        assertEquals("87,197,529", formatCount(87_197_529, en))
        assertEquals("1.2 M", formatCompact(1_200_000, en))
        assertEquals("1.3 B", formatCompact(1_300_000_000, en))
        assertEquals("42 k", formatCompact(42_000, en))
    }

    @Test
    fun `the device locale decides, so a French phone reads French`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            assertEquals("1,3 M", formatCompact(1_250_000))
            Locale.setDefault(Locale.US)
            assertEquals("1.3 M", formatCompact(1_250_000))
        } finally {
            Locale.setDefault(saved)
        }
    }
}
