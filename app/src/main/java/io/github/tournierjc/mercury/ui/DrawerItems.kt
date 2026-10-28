package io.github.tournierjc.mercury.ui

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
    data class Bot(val id: String) : DrawerRow
    data object AllCrew : DrawerRow

    /** The sessions heading says so, instead of leaving an empty space under it. */
    data object SessionHeader : DrawerRow
    data object NoSession : DrawerRow
    data class Thread(val id: String) : DrawerRow

    /** A group heading: « Second cerveau », « Configurer ». */
    data class SectionHeader(val section: DrawerSection) : DrawerRow

    /** A screen to open. [current] is true for the one on screen. */
    data class Screen(val screen: AppScreen, val current: Boolean = false) : DrawerRow

    data object NavDivider : DrawerRow
    data object SignOut : DrawerRow
}

/** The webapp's three sidebar groups. */
enum class DrawerSection { Work, SecondBrain, Configure }

/**
 * The drawer's rows, top to bottom: crew, sessions, then the screens. Pure, so the order and the
 * completeness of the list can be asserted without a screen (the sessions and the crew are two
 * lists the server owns, and the drawer is the only place they meet).
 */
fun drawerRows(
    botIds: List<String>,
    threadIds: List<String>,
    current: AppScreen = AppScreen.Chat,
): List<DrawerRow> = buildList {
    if (botIds.isNotEmpty()) {
        add(DrawerRow.CrewHeader(botIds.size))
        botIds.forEach { add(DrawerRow.Bot(it)) }
        add(DrawerRow.AllCrew)
    }
    add(DrawerRow.SessionHeader)
    if (threadIds.isEmpty()) add(DrawerRow.NoSession) else threadIds.forEach { add(DrawerRow.Thread(it)) }
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
