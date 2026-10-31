package io.github.tournierjc.mercury.ui

/**
 * Every surface the drawer can open, in the webapp's order.
 *
 * The webapp's sidebar has three groups — the work surfaces, the second brain, and the settings —
 * and the app used to have a flat list of six entries that mixed the three: no Tasks, no
 * Workspace, no Persons/Goals/Graph, and a single « Réglages » standing in for seven screens. This
 * enum is the spine: [DrawerRow] draws it, [ChatScreen] routes it, and a missing destination is a
 * compile error rather than a screen nobody can reach.
 */
enum class AppScreen {
    // The work, top of the sidebar.
    Status,
    Chat,
    /**
     * Every session, with a filter — reached from the drawer's last session row, not from the
     * menu: a drawer that lists everything is the drawer nobody scrolls.
     */
    Sessions,
    Bots,
    /**
     * What the bots can run, and whether it is signed in. A bot's tools are installed for the whole
     * fleet (the sandbox confines paths, not processes), so this belongs beside the fleet, not inside
     * one bot's sheet.
     */
    SandboxTools,
    Tasks,
    Boards,
    Workspace,

    // Second brain: four separate surfaces, not four tabs of one.
    Memory,
    Persons,
    Goals,
    Graph,

    // Configure: one screen per capability.
    Providers,
    Skills,
    Mcp,
    Plugins,
    Permissions,
    Schedules,
    Usage,

    // Below the groups: the app's own surfaces, then the instance settings.
    Deliverables,
    Bridge,
    /** The app's terminal/counters view; the webapp folded it into Status and Usage. */
    Console,
    Settings,
}
