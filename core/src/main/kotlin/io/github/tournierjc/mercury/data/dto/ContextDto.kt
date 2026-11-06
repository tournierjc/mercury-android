package io.github.tournierjc.mercury.data.dto

import kotlinx.serialization.Serializable

/**
 * The context gauge for one conversation — `GET /api/context/threads/:id`.
 *
 * Mirrors the daemon's `ContextSnapshot` (context-keeper-agent.ts): the model's
 * view of the history costs `viewTokens` against a rolling `budget`. Every field
 * defaults: an older runtime that lacks the route answers 404 and the pill hides.
 */
@Serializable
data class ContextSnapshotDto(
    val enabled: Boolean = false,
    val saverActive: Boolean = false,
    val budget: Int = 0,
    val historyTokens: Int = 0,
    val viewTokens: Int = 0,
    val needsCompression: Boolean = false,
    val cuts: Int = 0,
    val archivedTurns: Int = 0,
) {
    /** 0..1; the daemon's own yardstick — what the keeper would measure. */
    val fraction: Float
        get() = if (budget <= 0) 0f else (viewTokens.toFloat() / budget).coerceIn(0f, 1f)

    /** Whole percent, for the label. */
    val percent: Int get() = (fraction * 100).toInt()

    /** Nothing to draw: no route (an older daemon), no session, an empty view. */
    val isEmpty: Boolean get() = budget <= 0 || viewTokens <= 0
}
