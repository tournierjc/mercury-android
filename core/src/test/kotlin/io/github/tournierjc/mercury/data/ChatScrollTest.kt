package io.github.tournierjc.mercury.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le bouton « retour en bas » n'apparaît que quand il y a quelque chose à retrouver.
 */
class ChatScrollTest {

    @Test
    fun `an empty list is at the bottom`() {
        assertTrue(isAtBottom(lastVisibleIndex = -1, itemsCount = 0, lastItemBottom = 0, viewportEnd = 1200))
    }

    @Test
    fun `the last item, fully visible, is the bottom`() {
        assertTrue(isAtBottom(lastVisibleIndex = 9, itemsCount = 10, lastItemBottom = 1180, viewportEnd = 1200))
    }

    @Test
    fun `scrolled up by a message is not the bottom`() {
        assertFalse(isAtBottom(lastVisibleIndex = 7, itemsCount = 10, lastItemBottom = 1180, viewportEnd = 1200))
    }

    @Test
    fun `the last item cut by the viewport is not the bottom`() {
        assertFalse(isAtBottom(lastVisibleIndex = 9, itemsCount = 10, lastItemBottom = 1340, viewportEnd = 1200))
    }

    @Test
    fun `a few pixels short still counts as the bottom`() {
        assertTrue(isAtBottom(lastVisibleIndex = 9, itemsCount = 10, lastItemBottom = 1206, viewportEnd = 1200))
        assertFalse(isAtBottom(lastVisibleIndex = 9, itemsCount = 10, lastItemBottom = 1215, viewportEnd = 1200))
    }
}
