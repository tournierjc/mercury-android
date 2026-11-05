package io.github.tournierjc.mercury.context

/**
 * A rough token count, learned from the server.
 *
 * The app never runs a tokenizer: what it needs is a number it can compare with a budget, with a
 * margin. The count is characters over a ratio, exactly like ctxkeeper's `tokens()` — and the ratio
 * is *learned*: whenever the daemon reports a turn's real `tokensIn` for a text we hold, the chars
 * actually sent divided by the tokens actually read is the only honest ratio there is. A brand-new
 * session starts at 3.6 chars/token (the value ctxkeeper ships, measured on a served chat model)
 * and moves from there.
 *
 * Because an estimate can be low, every caller keeps a [headroom] beside the budget: an estimate is
 * a trigger for a cut, never a guarantee that the cut was enough.
 */
class TokenEstimate(ratio: Double = DEFAULT_RATIO) {

    /** Characters per token, as the server taught us. */
    var ratio: Double = ratio.coerceIn(MIN_RATIO, MAX_RATIO)
        private set

    /**
     * Fold one server-reported measurement into the ratio.
     *
     * [chars] is the text we estimated for, [promptTokens] what the server said it read — which
     * includes the chat template and any pinned notes, so the learned ratio runs a little high
     * (fewer tokens per char): the safe direction, since a high ratio over-estimates and cuts early
     * rather than letting an over-budget view through. A measurement with no content (an empty
     * conversation) is ignored: it would collapse the ratio.
     */
    fun observe(chars: Int, promptTokens: Long, weight: Double = LEARNING_WEIGHT) {
        if (chars <= 0 || promptTokens <= 0L) return
        val observed = chars.toDouble() / promptTokens.toDouble()
        if (!observed.isFinite() || observed <= 0.0) return
        ratio = (ratio * (1.0 - weight) + observed.coerceIn(MIN_RATIO, MAX_RATIO) * weight)
            .coerceIn(MIN_RATIO, MAX_RATIO)
    }

    /** The estimate for [text]: its characters over the learned ratio, rounded up. */
    fun estimate(text: String): Int = ceilChars(text.length)

    /** The estimate for several parts joined — the cost of a message, or of a whole view. */
    fun estimateAll(parts: Iterable<String>): Int {
        var chars = 0
        for (p in parts) chars += p.length + MESSAGE_OVERHEAD_CHARS
        return ceilChars(chars)
    }

    private fun ceilChars(chars: Int): Int =
        if (chars <= 0) 0 else Math.ceil(chars / ratio).toInt() + TOKEN_OVERHEAD

    companion object {
        /** ctxkeeper's starting ratio (its `Session.state["ratio"]`). */
        const val DEFAULT_RATIO = 3.6

        /** No served chat model compresses below ~1.5 chars/token or exceeds ~10 for prose. */
        const val MIN_RATIO = 1.5
        const val MAX_RATIO = 10.0

        /** Exponential smoothing: one bad usage line (a cached prompt, a tiny turn) cannot move the ratio alone. */
        const val LEARNING_WEIGHT = 0.2

        /** Per-message slack for role names, separators, the chat template. */
        const val MESSAGE_OVERHEAD_CHARS = 8

        /** Slack per message after the division, for template tokens the chars never carried. */
        const val TOKEN_OVERHEAD = 1
    }
}
