package io.github.tournierjc.mercury.ui

/**
 * One place the owner has been: the screen, the conversation open on it, and whether the sheet was
 * open when they left. Restoring all three is what makes Back feel like walking back rather than
 * being sent somewhere.
 */
data class NavStep(val screen: AppScreen, val conversation: String?, val drawerOpen: Boolean)

/** What the next press of Back does, decided before any screen is touched. */
sealed interface BackAction {
    /** The sheet is on top of everything: it closes first. */
    data object CloseDrawer : BackAction

    /** Walk back to a remembered place, sheet and conversation included. */
    data class Restore(val step: NavStep) : BackAction

    /** Nothing behind, but a conversation is open: close it, stay on the screen. */
    data object CloseConversation : BackAction

    /** Nothing left to close: let the system leave the app. */
    data object LeaveApp : BackAction
}

/**
 * The order a thumb expects from Back: the sheet first (it is on top), then the place the owner came
 * from, then the conversation open on the current screen, and only then the app itself.
 *
 * Pure so the order can be reasoned about — and tested — without a screen attached; the caller
 * still owns what each action means for its state.
 */
fun backAction(
    drawerOpen: Boolean,
    history: List<NavStep>,
    conversation: String?,
): BackAction = when {
    drawerOpen -> BackAction.CloseDrawer
    history.isNotEmpty() -> BackAction.Restore(history.last())
    conversation != null -> BackAction.CloseConversation
    else -> BackAction.LeaveApp
}

/**
 * The last thing the system button does in this app: leaving takes two presses.
 *
 * One press on the main screen arms the exit and says so; a second press inside [windowMs] leaves,
 * and anything later starts over. A single press that quits an app with unsent work in a composer
 * is the most expensive mis-tap a thumb can make, and a window this short costs nothing to a
 * deliberate exit.
 *
 * Pure and time-injected: the window is a fact to test, not a `System.currentTimeMillis()` buried in
 * a composable.
 */
class ExitGate(private val windowMs: Long = 2_000) {
    private var armedAt: Long? = null

    /** True when this press leaves the app; false when it armed the exit instead. */
    fun press(nowMs: Long): Boolean {
        val armed = armedAt
        if (armed != null && nowMs - armed in 0..windowMs) {
            armedAt = null
            return true
        }
        armedAt = nowMs
        return false
    }
}
