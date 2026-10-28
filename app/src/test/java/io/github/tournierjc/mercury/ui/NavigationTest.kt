package io.github.tournierjc.mercury.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Back used to send every screen home to the conversation: the second screen deep in a corner of the
 * app was unreachable again after one press, and a drawer that was open when the owner left it was
 * gone when they came back. These tests pin the order of the decisions, which no screenshot shows.
 */
class NavigationTest {

    private val memory = NavStep(AppScreen.Memory, conversation = null, drawerOpen = false)
    private val graph = NavStep(AppScreen.Graph, conversation = null, drawerOpen = false)

    @Test
    fun `the sheet closes before anything else, even with a path behind`() {
        assertEquals(
            BackAction.CloseDrawer,
            backAction(drawerOpen = true, history = listOf(memory, graph), conversation = "mac-lead"),
        )
    }

    @Test
    fun `back walks to the place just left, not home`() {
        assertEquals(BackAction.Restore(graph), backAction(drawerOpen = false, history = listOf(memory, graph), conversation = null))
    }

    @Test
    fun `the sheet is restored as it was left`() {
        // Reached from the drawer: coming back reopens it, and the next press closes it (above).
        val openedFromDrawer = NavStep(AppScreen.Tasks, conversation = null, drawerOpen = true)

        val action = backAction(drawerOpen = false, history = listOf(openedFromDrawer), conversation = null)

        assertEquals(BackAction.Restore(openedFromDrawer), action)
        assertEquals(true, (action as BackAction.Restore).step.drawerOpen)
    }

    @Test
    fun `a conversation on the current screen is closed before the screen itself`() {
        assertEquals(
            BackAction.CloseConversation,
            backAction(drawerOpen = false, history = emptyList(), conversation = "mac-qa"),
        )
    }

    @Test
    fun `with nothing behind, back leaves the app`() {
        assertEquals(BackAction.LeaveApp, backAction(drawerOpen = false, history = emptyList(), conversation = null))
    }

    @Test
    fun `the last step wins, and it is not consumed twice by the decision`() {
        val steps = listOf(memory, graph)

        assertEquals(BackAction.Restore(graph), backAction(false, steps, null))
        assertEquals(steps, listOf(memory, graph))
    }
}
