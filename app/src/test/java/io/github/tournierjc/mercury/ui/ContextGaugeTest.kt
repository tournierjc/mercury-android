package io.github.tournierjc.mercury.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The gauge's arithmetic is the one thing the owner trusts: it decides whether
 * they send another message or run /compact now. The keeper measures a view in
 * tokens; the bar measures it in the budget.
 */
class ContextGaugeTest {

    private fun snapshot(view: Int, budget: Int) = io.github.tournierjc.mercury.data.dto.ContextSnapshotDto(
        enabled = true,
        budget = budget,
        viewTokens = view,
    )

    @Test
    fun `percent reads the view against the keeper budget`() {
        assertEquals(20, snapshot(40_000, 200_000).percent)
        assertEquals(80, snapshot(160_000, 200_000).percent)
        // A view that somehow overflows never shows 101%.
        assertEquals(100, snapshot(300_000, 200_000).percent)
    }

    @Test
    fun `no budget means no gauge, not a division by zero`() {
        assertEquals(0f, snapshot(1_000, 0).fraction)
        // The bar checks isEmpty before drawing anything.
        assertEquals(true, io.github.tournierjc.mercury.data.dto.ContextSnapshotDto().isEmpty)
        assertEquals(false, snapshot(1_000, 200_000).isEmpty)
        assertEquals(true, snapshot(0, 200_000).isEmpty)
    }

    @Test
    fun `thousands read as k, millions as M`() {
        assertEquals("12k", fmtK(12_400))
        assertEquals("999", fmtK(999))
        assertEquals("1k", fmtK(1_000))
        assertEquals("2M", fmtK(2_500_000))
    }
}
