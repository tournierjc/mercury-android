package io.github.tournierjc.mercury.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The drawer is the one place the fleet, the sessions and the app's screens meet, and it is drawn
 * by a single list so that a growing crew cannot push the sessions out of reach. These tests guard
 * what a layout cannot show on its own: every destination is present, in the webapp's order, and
 * the group headings are where the webapp puts them.
 */
class DrawerItemsTest {

    private val rows = drawerRows(listOf("mac-lead", "scout"), listOf("t1"))

    private fun screens(current: AppScreen = AppScreen.Chat) =
        drawerRows(listOf("a"), listOf("t1"), current).filterIsInstance<DrawerRow.Screen>().map { it.screen }

    @Test
    fun `a crew of forty keeps every teammate, in the order the server sent them`() {
        val ids = (1..40).map { "bot-$it" }

        val rows = drawerRows(ids, listOf("t1"))

        assertEquals(ids, rows.filterIsInstance<DrawerRow.Bot>().map { it.id })
        assertEquals(DrawerRow.CrewHeader(40), rows.first())
    }

    @Test
    fun `the sessions come after the crew and before the screens`() {
        val crew = rows.indexOfFirst { it is DrawerRow.Bot }
        val header = rows.indexOf(DrawerRow.SessionHeader)
        val thread = rows.indexOfFirst { it is DrawerRow.Thread }
        val nav = rows.indexOfFirst { it is DrawerRow.Screen }

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
    fun `the work surfaces come first, in the webapp's order`() {
        assertEquals(
            listOf(
                AppScreen.Status,
                AppScreen.Chat,
                AppScreen.Bots,
                AppScreen.Tasks,
                AppScreen.Boards,
                AppScreen.Workspace,
            ),
            screens().take(6),
        )
    }

    @Test
    fun `the second brain and the configure group are both complete and in order`() {
        val all = screens()
        val secondBrain = all.subList(all.indexOf(AppScreen.Memory), all.indexOf(AppScreen.Graph) + 1)
        assertEquals(listOf(AppScreen.Memory, AppScreen.Persons, AppScreen.Goals, AppScreen.Graph), secondBrain)

        val configure = all.subList(all.indexOf(AppScreen.Providers), all.indexOf(AppScreen.Usage) + 1)
        assertEquals(
            listOf(
                AppScreen.Providers,
                AppScreen.Skills,
                AppScreen.Mcp,
                AppScreen.Plugins,
                AppScreen.Permissions,
                AppScreen.Schedules,
                AppScreen.Usage,
            ),
            configure,
        )
        val headings = drawerRows(emptyList(), emptyList()).filterIsInstance<DrawerRow.SectionHeader>()
        assertEquals(
            listOf(DrawerSection.SecondBrain, DrawerSection.Configure),
            headings.map { it.section },
        )
    }

    @Test
    fun `every destination of the app is reachable from the drawer`() {
        val missing = AppScreen.entries.filterNot { it in screens() }

        assertEquals("destinations without a row: $missing", emptyList<AppScreen>(), missing)
    }

    @Test
    fun `the screen on top is marked, and only it`() {
        val marked = drawerRows(listOf("a"), emptyList(), AppScreen.Graph)
            .filterIsInstance<DrawerRow.Screen>()
            .filter { it.current }

        assertEquals(listOf(AppScreen.Graph), marked.map { it.screen })
    }

    @Test
    fun `the app's own surfaces and the settings are below the groups, sign-out last`() {
        val all = screens()

        assertTrue(all.indexOf(AppScreen.Settings) > all.indexOf(AppScreen.Usage))
        assertTrue(all.indexOf(AppScreen.Deliverables) > all.indexOf(AppScreen.Usage))
        assertEquals(DrawerRow.SignOut, rows.last())
    }
}
