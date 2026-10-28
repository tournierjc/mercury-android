package io.github.tournierjc.mercury.ui

/**
 * One row of the drawer, in the order it is drawn.
 *
 * The drawer used to draw the crew in a fixed `Column` and the sessions in the one weighted child
 * below it: as soon as the crew grew, the fixed part took the whole sheet and nothing scrolled — the
 * sessions were squeezed to nothing and the crew itself was unreachable. The rows are data now, and
 * a single `LazyColumn` draws them, so the sheet scrolls whatever the number of teammates.
 */
sealed interface DrawerRow {
    /** The crew heading, drawn only when there is a crew. */
    data class CrewHeader(val bots: Int) : DrawerRow
    data class Bot(val id: String) : DrawerRow
    data object AllCrew : DrawerRow
    data object SessionHeader : DrawerRow

    /** The sessions heading says so, instead of leaving an empty space under it. */
    data object NoSession : DrawerRow
    data class Thread(val id: String) : DrawerRow
    data object NavDivider : DrawerRow
    data object Deliverables : DrawerRow
    data object Boards : DrawerRow
    data object Memory : DrawerRow
    data object Console : DrawerRow
    data object Bridge : DrawerRow
    data object Settings : DrawerRow
    data object SignOut : DrawerRow
}

/**
 * The drawer's rows, top to bottom: crew, sessions, then the screens. Pure, so the order and the
 * completeness of the list can be asserted without a screen (the sessions and the crew are two
 * lists the server owns, and the drawer is the only place they meet).
 */
fun drawerRows(botIds: List<String>, threadIds: List<String>): List<DrawerRow> = buildList {
    if (botIds.isNotEmpty()) {
        add(DrawerRow.CrewHeader(botIds.size))
        botIds.forEach { add(DrawerRow.Bot(it)) }
        add(DrawerRow.AllCrew)
    }
    add(DrawerRow.SessionHeader)
    if (threadIds.isEmpty()) add(DrawerRow.NoSession) else threadIds.forEach { add(DrawerRow.Thread(it)) }
    add(DrawerRow.NavDivider)
    add(DrawerRow.Deliverables)
    add(DrawerRow.Boards)
    add(DrawerRow.Memory)
    add(DrawerRow.Console)
    add(DrawerRow.Bridge)
    add(DrawerRow.Settings)
    add(DrawerRow.SignOut)
}
