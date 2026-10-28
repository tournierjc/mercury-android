package io.github.tournierjc.mercury.bots

import io.github.tournierjc.mercury.data.NoticeKey
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
        assertEquals(listOf(NoticeKey.CREW_NONE), WatchSummary.notices(0, 0).map { it.key })
    }

    @Test
    fun `one bot is singular`() {
        assertEquals(listOf(NoticeKey.CREW_ONE, NoticeKey.CREW_WAITING_NONE), WatchSummary.notices(1, 0).map { it.key })
        assertEquals(listOf(NoticeKey.CREW_ONE, NoticeKey.CREW_WAITING_ONE), WatchSummary.notices(1, 1).map { it.key })
    }

    @Test
    fun `several bots are plural`() {
        assertEquals(listOf(NoticeKey.CREW_MANY, NoticeKey.CREW_WAITING_NONE), WatchSummary.notices(4, 0).map { it.key })
        assertEquals(listOf("4", "2"), WatchSummary.notices(4, 2).flatMap { it.args })
    }

    @Test
    fun `waiting count is independent of the crew count`() {
        // Three bots waiting out of five: the first number is the roster, the second the queue.
        assertEquals(listOf(NoticeKey.CREW_MANY, NoticeKey.CREW_WAITING_MANY), WatchSummary.notices(5, 3).map { it.key })
        assertEquals(listOf(NoticeKey.CREW_MANY, NoticeKey.CREW_WAITING_ONE), WatchSummary.notices(12, 1).map { it.key })
    }
}
