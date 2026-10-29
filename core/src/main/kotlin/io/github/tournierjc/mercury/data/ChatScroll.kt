package io.github.tournierjc.mercury.data

/**
 * Whether the conversation is showing its last message in full.
 *
 * The round « back to the end » button is decided here rather than in the composable so the rule is
 * testable: a list is at the bottom when its last item is both the last one that exists and fully
 * inside the viewport. [slackPx] absorbs the rounding a lazy list leaves behind — a few pixels short
 * of the end is still the end, and a button that flickers at the pixel boundary is worse than no
 * button.
 *
 * An empty list counts as at the bottom: there is nowhere to go back to.
 */
fun isAtBottom(
    lastVisibleIndex: Int,
    itemsCount: Int,
    lastItemBottom: Int,
    viewportEnd: Int,
    slackPx: Int = 8,
): Boolean {
    if (itemsCount <= 0) return true
    if (lastVisibleIndex < itemsCount - 1) return false
    return lastItemBottom <= viewportEnd + slackPx
}
