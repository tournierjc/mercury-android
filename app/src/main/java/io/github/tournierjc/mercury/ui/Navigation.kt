package io.github.tournierjc.mercury.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset

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

/**
 * The one way two screens swap in this app, wherever they live: a shared-axis slide. The incoming
 * screen slips in from the far edge while fading up, the outgoing one slips out the other way
 * while fading down — a change of screen reads as a change of depth, not a teleport. `forward` is
 * a drill-in, `back` is its mirror.
 *
 * Centralised on purpose: the motion belongs to the shell, not to twenty screens each with their
 * own idea of it. The M3 surfaces that already animate (drawer, sheets, dialogs) keep their own
 * motion; this covers only the raw destination swaps.
 *
 * `exitProgress` is the progress of a predictive back-to-home gesture, 0 when idle: with nothing
 * left to close the shell answers for the gesture itself, and this is the cue it shows — the
 * whole shell shrinking the way the system preview would.
 */
@Composable
fun ScreenSwitch(
    target: AppScreen,
    forward: Boolean,
    exitProgress: () -> Float,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedContentScope.(AppScreen) -> Unit,
) {
    AnimatedContent(
        targetState = target,
        modifier = modifier.graphicsLayer {
            val progress = exitProgress()
            val scale = 1f - 0.05f * progress
            scaleX = scale
            scaleY = scale
            alpha = 1f - 0.2f * progress
        },
        transitionSpec = {
            val drift = tween<IntOffset>(260, easing = FastOutSlowInEasing)
            val fade = tween<Float>(260, easing = FastOutSlowInEasing)
            if (forward) {
                (slideInHorizontally(drift) { it / 4 } + fadeIn(fade)) togetherWith
                    (slideOutHorizontally(drift) { -it / 4 } + fadeOut(fade))
            } else {
                (slideInHorizontally(drift) { -it / 4 } + fadeIn(fade)) togetherWith
                    (slideOutHorizontally(drift) { it / 4 } + fadeOut(fade))
            }
        },
        label = "ScreenSwitch",
        content = content,
    )
}
