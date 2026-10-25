package io.github.tournierjc.mercury.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun `uptime is compacted by magnitude`() {
        assertEquals("45 s", formatUptime(45))
        assertEquals("12 min", formatUptime(12 * 60 + 30))
        assertEquals("3 h 04 min", formatUptime(3 * 3_600 + 4 * 60))
        assertEquals("2 j 05 h", formatUptime(2 * 86_400 + 5 * 3_600 + 59))
        assertEquals("—", formatUptime(0))
    }

    @Test
    fun `tokens use french decimal separator`() {
        assertEquals("999", formatTokens(999))
        assertEquals("12,2k", formatTokens(12_209))
        assertEquals("200k", formatTokens(200_000))
        assertEquals("1,4M", formatTokens(1_400_000))
    }
}
