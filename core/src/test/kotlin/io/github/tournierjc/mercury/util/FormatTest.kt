package io.github.tournierjc.mercury.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * The one formatter module. The tests that talk about a locale say so explicitly — the device's
 * locale must never decide what a test expects.
 */
class FormatTest {

    private val fr = Locale.FRANCE
    private val en = Locale.US
    private val nbsp = '\u202F'

    @Test
    fun `uptime is compacted by magnitude`() {
        assertEquals("45 s", formatUptime(45))
        assertEquals("12 min", formatUptime(12 * 60 + 30))
        assertEquals("3 h 04 min", formatUptime(3 * 3_600 + 4 * 60))
        assertEquals("2 j 05 h", formatUptime(2 * 86_400 + 5 * 3_600 + 59))
        assertEquals("—", formatUptime(0))
    }

    @Test
    fun `counts are grouped with a narrow no-break space in French`() {
        assertEquals("87${nbsp}197${nbsp}529", formatCount(87_197_529, fr))
        assertEquals("1${nbsp}000", formatCount(1_000, fr))
        assertEquals("999", formatCount(999, fr))
        assertEquals("0", formatCount(0, fr))
        assertEquals("-42${nbsp}000", formatCount(-42_000, fr))
    }

    @Test
    fun `English counts are comma-grouped`() {
        assertEquals("87,197,529", formatCount(87_197_529, en))
        assertEquals("0", formatCount(0, en))
    }

    @Test
    fun `tokens are shortened to k M and Md with a comma decimal in French`() {
        assertEquals("812", formatTokens(812, fr))
        assertEquals("42 k", formatTokens(42_000, fr))
        assertEquals("42,5 k", formatTokens(42_500, fr))
        assertEquals("1,2 M", formatTokens(1_200_000, fr))
        assertEquals("81,2 M", formatTokens(81_180_986, fr))
        assertEquals("2,1 Md", formatTokens(2_100_000_000, fr))
    }

    @Test
    fun `a whole magnitude loses its decimal`() {
        assertEquals("1 M", formatTokens(1_000_000, fr))
        assertEquals("80 M", formatTokens(80_000_000, fr))
        assertEquals("3 k", formatTokens(3_000, fr))
    }

    @Test
    fun `token rounding is half up, not truncation`() {
        // 1 249 999 truncates to "1,2 M" and rounds to "1,2 M"; 1 250 000 must round up.
        assertEquals("1,2 M", formatTokens(1_249_999, fr))
        assertEquals("1,3 M", formatTokens(1_250_000, fr))
        assertEquals("1,1 M", formatTokens(1_050_000, fr))
    }

    @Test
    fun `English tokens get their own separators and their own billion`() {
        assertEquals("1.2 M", formatTokens(1_200_000, en))
        assertEquals("1.3 B", formatTokens(1_300_000_000, en))
        assertEquals("42 k", formatTokens(42_000, en))
        assertEquals("1.3 M", formatTokens(1_250_000, en))
    }

    @Test
    fun `a negative token count keeps its sign`() {
        assertEquals("-42 k", formatTokens(-42_000, fr))
    }

    @Test
    fun `sizes read in the locale's units`() {
        assertEquals("512 o", formatBytes(512, fr))
        assertEquals("1,5 ko", formatBytes(1536, fr))
        assertEquals("5 Mo", formatBytes(5L * 1024 * 1024, fr))
        assertEquals("1,5 Go", formatBytes((1.5 * 1024 * 1024 * 1024).toLong(), fr))
        assertEquals("—", formatBytes(0, fr))

        assertEquals("512 B", formatBytes(512, en))
        assertEquals("1.5 kB", formatBytes(1536, en))
        assertEquals("5 MB", formatBytes(5L * 1024 * 1024, en))
        assertEquals("2 GB", formatBytes(2L * 1024 * 1024 * 1024, en))
    }

    @Test
    fun `a size that rounds to the next unit steps up`() {
        // One byte under a mebibyte: truncation would print "1024 ko".
        assertEquals("1 Mo", formatBytes(1024L * 1024 - 1, fr))
    }

    @Test
    fun `costs print the way the board prints them`() {
        assertEquals("$0,47", formatCost(0.47, fr))
        assertEquals("$12", formatCost(12.0, fr))
        // A cost that would round to zero must not read as free.
        assertEquals("<$0,001", formatCost(0.0009, fr))
        assertEquals("$0", formatCost(0.0, fr))
        assertEquals("$0.47", formatCost(0.47, en))
        assertEquals("$12", formatCost(12.0, en))
    }

    @Test
    fun `the device locale decides, so a French phone reads French`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            assertEquals("1,3 M", formatTokens(1_250_000))
            assertEquals("1,5 ko", formatBytes(1536))
            Locale.setDefault(Locale.US)
            assertEquals("1.3 M", formatTokens(1_250_000))
            assertEquals("1.5 kB", formatBytes(1536))
        } finally {
            Locale.setDefault(saved)
        }
    }
}
