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
