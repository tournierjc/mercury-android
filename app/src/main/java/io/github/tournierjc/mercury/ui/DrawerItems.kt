package io.github.tournierjc.mercury.ui

import io.github.tournierjc.mercury.data.FleetRow

/**
 * One row of the drawer, in the order it is drawn.
 *
 * The drawer used to draw the crew in a fixed `Column` and the sessions in the one weighted child
 * below it: as soon as the crew grew, the fixed part took the whole sheet and nothing scrolled — the
 * sessions were squeezed to nothing and the crew itself was unreachable. The rows are data now, and
 * a single `LazyColumn` draws them, so the sheet scrolls whatever the number of teammates.
 *
 * The navigation entries follow the webapp's three groups (work / second brain / configure) plus
 * the app's own two surfaces and the instance settings — see [AppScreen].
 */
sealed interface DrawerRow {
    /** The crew heading, drawn only when there is a crew. */
    data class CrewHeader(val bots: Int) : DrawerRow

    /**
     * A lead with a crew: the row carries the count and a disclosure, and its crew is only drawn
     * when [open]. Folded by default — a fleet of ten used to read as ten equals, and the one thing
     * the drawer could not show was who answers to whom.
     */
    data class Lead(val id: String, val crew: Int, val open: Boolean) : DrawerRow

    /** [under] indents a crew member beneath its lead; a solo bot is never indented. */
    data class Bot(val id: String, val under: Boolean = false) : DrawerRow
    data object AllCrew : DrawerRow

    /** The sessions heading says so, instead of leaving an empty space under it. */
    data object SessionHeader : DrawerRow
    data object NoSession : DrawerRow
    data class Thread(val id: String) : DrawerRow

    /**
     * The way to the rest of them. [hidden] is what the drawer is not showing, so the row can say
     * it instead of the owner wondering whether the list is complete.
     */
    data class AllSessions(val hidden: Int) : DrawerRow

    /** A group heading: « Second cerveau », « Configurer ». */
    data class SectionHeader(val section: DrawerSection) : DrawerRow

    /** A screen to open. [current] is true for the one on screen. */
    data class Screen(val screen: AppScreen, val current: Boolean = false) : DrawerRow

    data object NavDivider : DrawerRow
    data object SignOut : DrawerRow
}

/** How many sessions the drawer lists before handing over to the sessions screen. */
const val SESSION_LIMIT = 5

/** The webapp's three sidebar groups. */
enum class DrawerSection { Work, SecondBrain, Configure }

/**
 * The drawer's rows, top to bottom: crew, sessions, then the screens. Pure, so the order and the
 * completeness of the list can be asserted without a screen (the sessions and the crew are two
 * lists the server owns, and the drawer is the only place they meet).
 */
fun drawerRows(
    fleet: List<FleetRow>,
    threadIds: List<String>,
    current: AppScreen = AppScreen.Chat,
    /** The leads whose crew is unfolded. Folded is the default, and the only state we keep. */
    openLeads: Set<String> = emptySet(),
): List<DrawerRow> = buildList {
    if (fleet.isNotEmpty()) {
        // The heading counts the whole fleet, folded or not: a drawer that said « 4 » while holding
        // ten teammates would be lying about what is behind « Tous les équipiers ».
        val total = fleet.sumOf { row -> if (row is FleetRow.Lead) 1 + row.crewIds.size else 1 }
        add(DrawerRow.CrewHeader(total))
        fleet.forEach { row ->
            when (row) {
                is FleetRow.Solo -> add(DrawerRow.Bot(row.id))
                is FleetRow.Lead -> {
                    if (row.crewIds.isEmpty()) {
                        // A lead nobody answers to yet is one row: nothing to disclose.
                        add(DrawerRow.Bot(row.id))
                    } else {
                        val open = row.id in openLeads
                        add(DrawerRow.Lead(row.id, row.crewIds.size, open))
                        if (open) row.crewIds.forEach { add(DrawerRow.Bot(it, under = true)) }
                    }
                }
            }
        }
        add(DrawerRow.AllCrew)
    }
    add(DrawerRow.SessionHeader)
    if (threadIds.isEmpty()) {
        add(DrawerRow.NoSession)
    } else {
        // A drawer is a way in, not an archive: five sessions are what fits without pushing the
        // navigation off the sheet, and the rest live one tap away, with a filter.
        val shown = threadIds.take(SESSION_LIMIT)
        shown.forEach { add(DrawerRow.Thread(it)) }
        if (threadIds.size > shown.size) add(DrawerRow.AllSessions(hidden = threadIds.size - shown.size))
    }
    // The work, in the webapp's order — one named group, not a headerless block behind a rule.
    add(DrawerRow.SectionHeader(DrawerSection.Work))
    listOf(
        AppScreen.Status,
        AppScreen.Chat,
        AppScreen.Bots,
        AppScreen.Tasks,
        AppScreen.Boards,
        AppScreen.Workspace,
    ).forEach { add(DrawerRow.Screen(it, it == current)) }

    add(DrawerRow.SectionHeader(DrawerSection.SecondBrain))
    listOf(AppScreen.Memory, AppScreen.Persons, AppScreen.Goals, AppScreen.Graph)
        .forEach { add(DrawerRow.Screen(it, it == current)) }

    add(DrawerRow.SectionHeader(DrawerSection.Configure))
    listOf(
        AppScreen.Providers,
        AppScreen.Skills,
        AppScreen.Mcp,
        AppScreen.Plugins,
        AppScreen.Permissions,
        AppScreen.Schedules,
        AppScreen.Usage,
    ).forEach { add(DrawerRow.Screen(it, it == current)) }

    // The app's own surfaces (a share sheet the webapp does not have, the phone bridge), then the
    // settings — a dedicated screen, where a settings entry is looked for.
    add(DrawerRow.NavDivider)
    listOf(AppScreen.Deliverables, AppScreen.Console, AppScreen.Bridge, AppScreen.Settings)
        .forEach { add(DrawerRow.Screen(it, it == current)) }

    add(DrawerRow.SignOut)
}
