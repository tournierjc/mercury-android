package io.github.tournierjc.mercury.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The last press the system button can make in this app. One press on the main screen asks, the
 * second leaves — and a press long after the first starts over rather than quitting on a stale
 * question nobody saw.
 */
class ExitGateTest {

    private val gate = ExitGate(windowMs = 2_000)

    @Test
    fun `the first press arms instead of leaving`() {
        assertFalse(gate.press(nowMs = 0))
    }

    @Test
    fun `a second press inside the window leaves`() {
        gate.press(nowMs = 1_000)

        assertTrue(gate.press(nowMs = 2_500))
    }

    @Test
    fun `a second press after the window asks again`() {
        gate.press(nowMs = 1_000)

        assertFalse("asked 4 s later, the question is stale", gate.press(nowMs = 5_000))
    }

    @Test
    fun `leaving disarms, so the next press asks again`() {
        gate.press(nowMs = 0)
        assertTrue(gate.press(nowMs = 500))

        assertFalse("the gate is not still open", gate.press(nowMs = 900))
    }

    @Test
    fun `three quick presses leave once, on the second`() {
        assertFalse(gate.press(nowMs = 0))
        assertTrue(gate.press(nowMs = 300))
        assertFalse(gate.press(nowMs = 600))
    }

    @Test
    fun `a clock that steps backwards does not quit`() {
        // Nothing should leave the app because time moved the wrong way (a paused device resuming).
        gate.press(nowMs = 10_000)

        assertFalse(gate.press(nowMs = 9_000))
    }
}
