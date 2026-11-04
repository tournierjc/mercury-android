package io.github.tournierjc.mercury.context

/**
 * What a cut did, in the numbers the consistency gate reads.
 *
 * [beforeTokens] is the estimate of the full view before the cut, [afterTokens] the estimate of the
 * view the cut leaves (opening + notes + kept tail + archive notice). [droppedChars] and
 * [removedTurns] are the hard facts underneath: characters no longer in view, turns moved to the
 * archive. The gate in [ContextKeeper] refuses any cut that cannot show all of them.
 */
data class CutReport(
    val beforeTokens: Int,
    val afterTokens: Int,
    val droppedChars: Int,
    val removedTurns: Int,
    val archivedThrough: Int,
)

/**
 * Everything the archive keeps of one conversation: the turns, verbatim, in order.
 *
 * The rule that makes compression safe at all: **nothing is ever lost, only moved out of view**. The
 * gate in [ContextKeeper] proves it per cut — every turn the cut removed must be findable here,
 * byte-identical, before the cut is reported as done.
 */
class TurnArchive {
    private val turns = mutableListOf<ContextTurn>()

    val size: Int get() = turns.size
    fun isEmpty(): Boolean = turns.isEmpty()
    fun all(): List<ContextTurn> = turns.toList()

    /** Append the turns a cut moved out. The keeper calls this *before* applying the cut. */
    fun archive(turns: List<ContextTurn>) {
        this.turns += turns
    }

    /**
     * Drop the last [count] turns — only for a cut the keeper rolled back. The public surface has no
     * remove for anything else: an archive that lets material vanish is the bug this whole module
     * exists to prevent, so removal stays `internal` and the keeper calls it only under its lock.
     */
    internal fun removeTail(count: Int) {
        repeat(count) { if (turns.isNotEmpty()) turns.removeAt(turns.size - 1) }
    }

    /**
     * Search the archive the way ctxkeeper's `recall` does: case-insensitive substring over the
     * rendered text of each turn, newest matches first, at most [limit] hits.
     *
     * Each hit is rendered `turn N (role): sentence` — the turn number travels with the sentence so
     * the model can cite where it read something.
     */
    fun search(pattern: String, limit: Int = 20): List<String> {
        if (pattern.isBlank()) return emptyList()
        val needle = pattern.lowercase()
        val hits = mutableListOf<String>()
        for (turn in turns.asReversed()) {
            if (hits.size >= limit) break
            for (sentence in turn.text.split(SENTENCE_BREAK)) {
                val s = sentence.trim()
                if (s.length >= 2 && s.lowercase().contains(needle)) {
                    hits += "turn ${turn.number} (${turn.role.name.lowercase()}): ${s.take(MAX_SNIPPET)}"
                    if (hits.size >= limit) break
                }
            }
        }
        return hits
    }

    /** How many archived turns contain [pattern] — the count line of a `recall` result. */
    fun countMatching(pattern: String): Int {
        if (pattern.isBlank()) return 0
        val needle = pattern.lowercase()
        return turns.count { it.text.lowercase().contains(needle) }
    }

    /** The whole text of one archived turn, by its number; null when it is not here. */
    fun turnAt(number: Int): ContextTurn? = turns.firstOrNull { it.number == number }

    companion object {
        /** ctxkeeper splits a search result into sentences; a lone break char is not a sentence. */
        private val SENTENCE_BREAK = Regex("[.!?]\\s+|\\n+")
        private const val MAX_SNIPPET = 300
    }
}
