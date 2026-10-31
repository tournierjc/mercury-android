package io.github.tournierjc.mercury.ui

import io.github.tournierjc.mercury.data.FleetRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The drawer is the one place the fleet, the sessions and the app's screens meet, and it is drawn
 * by a single list so that a growing crew cannot push the sessions out of reach. These tests guard
 * what a layout cannot show on its own: every destination is present, in the webapp's order, the
 * group headings are where the webapp puts them, and the roster arrives folded on its leads.
 */
class DrawerItemsTest {

    private fun solo(vararg ids: String): List<FleetRow> = ids.map { FleetRow.Solo(it) }

    private fun lead(id: String, vararg crew: String): FleetRow = FleetRow.Lead(id, crew.toList())

    private val fleet = listOf(lead("mac-lead", "scout"), FleetRow.Solo("loner"))
    private val rows = drawerRows(fleet, listOf("t1"))

    private fun screens(current: AppScreen = AppScreen.Chat) =
        drawerRows(solo("a"), listOf("t1"), current).filterIsInstance<DrawerRow.Screen>().map { it.screen }

    @Test
    fun `a crew of forty keeps every teammate, in the order the server sent them`() {
        val ids = (1..40).map { "bot-$it" }

        val rows = drawerRows(solo(*ids.toTypedArray()), listOf("t1"))

        assertEquals(ids, rows.filterIsInstance<DrawerRow.Bot>().map { it.id })
        assertEquals(DrawerRow.CrewHeader(40), rows.first())
    }

    @Test
    fun `a lead arrives folded, and its crew is not drawn`() {
        val leadRow = rows.filterIsInstance<DrawerRow.Lead>().single()

        assertEquals(DrawerRow.Lead("mac-lead", 1, open = false), leadRow)
        assertEquals(listOf("loner"), rows.filterIsInstance<DrawerRow.Bot>().map { it.id })
    }

    @Test
    fun `unfolding a lead draws its crew under it, indented, in the server's order`() {
        val unfolded = drawerRows(listOf(lead("mac-lead", "qa", "web", "vue")), listOf("t1"), openLeads = setOf("mac-lead"))

        assertEquals(
            listOf("mac-lead", "qa", "web", "vue"),
            unfolded.filterIsInstance<DrawerRow.Lead>().map { it.id } +
                unfolded.filterIsInstance<DrawerRow.Bot>().map { it.id },
        )
        assertTrue("crew members are indented", unfolded.filterIsInstance<DrawerRow.Bot>().all { it.under })
        assertEquals(DrawerRow.Lead("mac-lead", 3, open = true), unfolded.filterIsInstance<DrawerRow.Lead>().single())
    }

    @Test
    fun `the heading counts the whole fleet, folded crew included`() {
        // Folding hides rows; it must not hide teammates from the count behind « Tous les équipiers ».
        assertEquals(DrawerRow.CrewHeader(4), drawerRows(listOf(lead("mac-lead", "qa", "web"), FleetRow.Solo("loner")), listOf("t1")).first())
    }

    @Test
    fun `a lead nobody answers to yet is one row, with nothing to disclose`() {
        val rows = drawerRows(listOf(lead("mac-lead")), listOf("t1"))

        assertTrue(rows.none { it is DrawerRow.Lead })
        assertEquals(listOf("mac-lead"), rows.filterIsInstance<DrawerRow.Bot>().map { it.id })
    }

    @Test
    fun `« Tous les équipiers » stays the last row of the crew block`() {
        val index = rows.indexOf(DrawerRow.AllCrew)

        assertTrue(index > 0)
        assertTrue("nothing of the crew after it", rows.subList(index + 1, rows.size).none {
            it is DrawerRow.Bot || it is DrawerRow.Lead || it is DrawerRow.CrewHeader
        })
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
        val rows = drawerRows(solo("a"), emptyList())

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
    fun `the three groups are complete and in the webapp's order`() {
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
        val work = all.subList(all.indexOf(AppScreen.Status), all.indexOf(AppScreen.Workspace) + 1)
        assertEquals(
            listOf(
                AppScreen.Status,
                AppScreen.Chat,
                AppScreen.Bots,
                AppScreen.Tasks,
                AppScreen.Boards,
                AppScreen.Workspace,
            ),
            work,
        )
        // Every group is named: a headerless block of six screens behind a divider was the drawer
        // reading as an undifferentiated list.
        val headings = drawerRows(emptyList(), emptyList()).filterIsInstance<DrawerRow.SectionHeader>()
        assertEquals(
            listOf(DrawerSection.Work, DrawerSection.SecondBrain, DrawerSection.Configure),
            headings.map { it.section },
        )
    }

    @Test
    fun `every destination of the app is reachable from the drawer`() {
        // Sessions is the exception, and on purpose: it is the screen the session list hands over
        // to (the `AllSessions` row), not a menu entry — the webapp's sidebar has no such screen,
        // and the drawer mirrors that sidebar.
        val missing = AppScreen.entries
            .filterNot { it == AppScreen.Sessions }
            .filterNot { it in screens() }

        assertEquals("destinations without a row: $missing", emptyList<AppScreen>(), missing)
    }

    @Test
    fun `the screen on top is marked, and only it`() {
        val marked = drawerRows(solo("a"), emptyList(), AppScreen.Graph)
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

    @Test
    fun `the drawer lists five sessions and hands the rest over`() {
        val ids = (1..8).map { "s$it" }

        val rows = drawerRows(solo("a"), ids)

        assertEquals(ids.take(SESSION_LIMIT), rows.filterIsInstance<DrawerRow.Thread>().map { it.id })
        assertEquals(DrawerRow.AllSessions(hidden = 3), rows.filterIsInstance<DrawerRow.AllSessions>().single())
    }

    @Test
    fun `a short list gets no hand-over row`() {
        val rows = drawerRows(solo("a"), listOf("s1", "s2"))

        assertEquals(2, rows.filterIsInstance<DrawerRow.Thread>().size)
        assertTrue(rows.filterIsInstance<DrawerRow.AllSessions>().isEmpty())
    }
}
