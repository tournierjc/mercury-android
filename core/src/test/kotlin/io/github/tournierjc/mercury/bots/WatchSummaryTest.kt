package io.github.tournierjc.mercury.bots

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The sentence the persistent notification carries. Small, but it is the only part of the
 * background watch that can be proven without a device — and a wrong plural or a "0 équipiers"
 * is exactly the kind of thing nobody notices until it is on someone's lock screen.
 */
class WatchSummaryTest {

    @Test
    fun `nothing connected yet says so without promising a watch`() {
        assertEquals("Aucun équipier — connexion au serveur en cours", WatchSummary.text(0, 0))
    }

    @Test
    fun `one bot is singular`() {
        assertEquals("1 équipier · rien ne vous attend", WatchSummary.text(1, 0))
        assertEquals("1 équipier · 1 vous attend", WatchSummary.text(1, 1))
    }

    @Test
    fun `several bots are plural`() {
        assertEquals("4 équipiers · rien ne vous attend", WatchSummary.text(4, 0))
        assertEquals("4 équipiers · 2 vous attendent", WatchSummary.text(4, 2))
    }

    @Test
    fun `waiting count is independent of the crew count`() {
        // Three bots waiting out of five: the first number is the roster, the second the queue.
        assertEquals("5 équipiers · 3 vous attendent", WatchSummary.text(5, 3))
        assertEquals("12 équipiers · 1 vous attend", WatchSummary.text(12, 1))
    }

    @Test
    fun `the title is the one the shade shows`() {
        assertEquals("Mercury — veille", WatchSummary.TITLE)
    }
}
