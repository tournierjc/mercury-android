package io.github.tournierjc.mercury.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The drawer is the one place the fleet and the sessions meet, and it is drawn by a single list so
 * that a growing crew cannot push the sessions out of reach. These tests guard the two things a
 * layout cannot show on its own: every row is present, and the order is the one the owner expects.
 */
class DrawerItemsTest {

    @Test
    fun `a crew of forty keeps every teammate, in the order the server sent them`() {
        val ids = (1..40).map { "bot-$it" }

        val rows = drawerRows(ids, listOf("t1"))

        val bots = rows.filterIsInstance<DrawerRow.Bot>().map { it.id }
        assertEquals(ids, bots)
        assertEquals(DrawerRow.CrewHeader(40), rows.first())
    }

    @Test
    fun `the sessions come after the crew and before the screens`() {
        val rows = drawerRows(listOf("a", "b"), listOf("t1", "t2"))

        val crew = rows.indexOfFirst { it is DrawerRow.Bot }
        val header = rows.indexOf(DrawerRow.SessionHeader)
        val thread = rows.indexOfFirst { it is DrawerRow.Thread }
        val nav = rows.indexOf(DrawerRow.Deliverables)

        assertTrue("crew before sessions", crew < header)
        assertTrue("sessions before the screens", header < thread && thread < nav)
    }

    @Test
    fun `no crew means no crew heading, and an empty crew cannot swallow the sessions`() {
        val rows = drawerRows(emptyList(), listOf("t1"))

        assertTrue(rows.none { it is DrawerRow.CrewHeader || it is DrawerRow.AllCrew || it is DrawerRow.Bot })
        assertTrue(rows.contains(DrawerRow.SessionHeader))
        assertTrue(rows.contains(DrawerRow.Thread("t1")))
    }

    @Test
    fun `no session says so instead of leaving the space empty`() {
        val rows = drawerRows(listOf("a"), emptyList())

        assertTrue(rows.contains(DrawerRow.NoSession))
        assertTrue(rows.none { it is DrawerRow.Thread })
    }

    @Test
    fun `the screens are all there, sign-out last`() {
        val rows = drawerRows(listOf("a"), listOf("t1"))
        val screens = rows.filter {
            it is DrawerRow.Deliverables || it is DrawerRow.Boards || it is DrawerRow.Memory ||
                it is DrawerRow.Console || it is DrawerRow.Bridge || it is DrawerRow.Settings
        }

        assertEquals(6, screens.size)
        assertEquals(DrawerRow.SignOut, rows.last())
    }
}
