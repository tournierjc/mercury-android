package io.github.tournierjc.mercury.context

/**
 * The notes block the keeper maintains for one conversation.
 *
 * Mirrors ctxkeeper's NOTES layout (`ctxkeeper.py`'s `NOTES_REQUEST`): a `RULES` section of standing
 * rules and methods that can never be dropped, a `STATE` section of current facts later work depends
 * on, and an `OPEN` section of what is unfinished. The section layout is a contract — the delta
 * parser, the pin, and the consistency gate all read it — so it is normalised here and nowhere else.
 */
data class Notes(
    val rules: List<String> = emptyList(),
    /** Ordered `name: value` facts. A name keeps its place; a new name is appended. */
    val state: List<Pair<String, String>> = emptyList(),
    val open: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = rules.isEmpty() && state.isEmpty() && open.isEmpty()

    /** The block as the model sees it — the exact layout [parse] accepts back. */
    fun render(): String = buildString {
        appendLine("RULES")
        if (rules.isEmpty()) appendLine("(none)") else rules.forEach { appendLine(it) }
        appendLine("STATE")
        if (state.isEmpty()) appendLine("(none)") else state.forEach { appendLine("${it.first}: ${it.second}") }
        appendLine("OPEN")
        if (open.isEmpty()) appendLine("(none)") else open.forEach { appendLine(it) }
    }.trimEnd()

    /**
     * The notes as they are shown in the kept view: the heading the keeper pins above them, then the
     * block. Mirrors ctxkeeper's `PIN_HEAD` — the model is told how many messages live in the
     * archive, so a short view does not read as a short conversation.
     */
    fun pinned(archivedCount: Int): String =
        "[[NOTES kept by the context keeper: $archivedCount earlier messages are in the archive]]\n" + render()

    companion object {
        val EMPTY = Notes()

        private val HEADINGS = setOf("RULES", "STATE", "OPEN")

        /**
         * Read a model's reply back into [Notes]; null when it does not use the layout (a reply with
         * no `RULES` heading is the model continuing the task instead of writing notes — the same
         * rejection ctxkeeper's `has_layout` makes).
         *
         * Text before `RULES` is dropped (a thinking preamble), `(none)` under a heading is no
         * entries, and blank lines are never entries.
         */
        fun parse(text: String): Notes? {
            val lines = text.lineSequence().map { it.trim() }.toList()
            val start = lines.indexOfFirst { it == "RULES" }
            if (start < 0) return null
            var section = "RULES"
            val rules = mutableListOf<String>()
            val state = mutableListOf<Pair<String, String>>()
            val open = mutableListOf<String>()
            for (line in lines.drop(start + 1)) {
                if (line in HEADINGS) {
                    section = line
                    continue
                }
                if (line.isEmpty() || line.equals("(none)", ignoreCase = true)) continue
                when (section) {
                    "RULES" -> rules += line
                    "STATE" -> state += factOf(line)
                    "OPEN" -> open += line
                }
            }
            return Notes(rules, state, open)
        }

        /** `(name, value)` of a STATE line: `name: value`, `name = value`, or `name rest`. */
        fun factOf(line: String): Pair<String, String> {
            val colon = line.indexOf(':')
            val eq = line.indexOf('=')
            val cut = when {
                colon >= 0 && (eq < 0 || colon < eq) -> colon
                eq >= 0 -> eq
                else -> -1
            }
            return if (cut <= 0) {
                val sp = line.indexOf(' ')
                if (sp > 0) line.substring(0, sp).trim() to line.substring(sp + 1).trim() else line to ""
            } else {
                line.substring(0, cut).trim() to line.substring(cut + 1).trim()
            }
        }
    }
}

/**
 * The changes-only reply the model writes before a cut (ctxkeeper's `INPLACE_REQUEST` layout):
 * `NEW RULES`, `CHANGES` (one `name: value` line per STATE change, in order, so a thing changed
 * twice has two lines), `OPEN`. A missing `OPEN` heading means the old OPEN section stays — which
 * is why [open] is nullable here and [changes] is not: a reply that skipped `CHANGES` did not go
 * through the turns and is rejected outright.
 */
data class NotesDelta(
    val rules: List<String>,
    val changes: List<Pair<String, String>>,
    /** null = the reply carried no OPEN heading: keep the old section. */
    val open: List<String>?,
) {
    companion object {
        private val HEADINGS = setOf("NEW RULES", "CHANGES", "OPEN")

        /** null when the reply does not use the layout (no `NEW RULES` heading). */
        fun parse(text: String): NotesDelta? {
            val lines = text.lineSequence().map { it.trim() }.toList()
            val start = lines.indexOfFirst { it == "NEW RULES" }
            if (start < 0) return null
            var section = "NEW RULES"
            var seenChanges = false
            var seenOpen = false
            val rules = mutableListOf<String>()
            val changes = mutableListOf<Pair<String, String>>()
            val open = mutableListOf<String>()
            for (line in lines.drop(start + 1)) {
                if (line in HEADINGS) {
                    section = line
                    if (line == "CHANGES") seenChanges = true
                    if (line == "OPEN") seenOpen = true
                    continue
                }
                if (line.isEmpty() || line.equals("(none)", ignoreCase = true)) continue
                when (section) {
                    "NEW RULES" -> rules += line
                    "CHANGES" -> changes += Notes.factOf(line)
                    "OPEN" -> open += line
                }
            }
            // A reply with no CHANGES heading at all is the model answering the task, not writing
            // notes: accepting it would silently pass a cut that folds nothing.
            if (!seenChanges) return null
            return NotesDelta(rules, changes, if (seenOpen) open else null)
        }
    }
}

/**
 * Applies a [NotesDelta] to the current [Notes] with ctxkeeper's safety rules — the ones that turn
 * a model-written summary from a lossy compression into a safe one:
 *
 * - **A RULES line can never disappear.** Lines the model does not mention stay, in order; a new
 *   rule that restates an existing one (same words, ≥60% word overlap) is not added twice.
 * - **A STATE line keeps its place**; a change replaces its name's value or appends a new name.
 *   `name: removed` drops the line.
 * - **`name: 120 + 49 = 169` stores its result** (the working is kept in the value's tail only when
 *   it does not compute); a value that is plainly false — `= 168` where the working says 169 — is
 *   [applied] refuses, so the caller asks the model once more.
 */
object NotesDeltaApplier {

    /** The delta was refused: [reason] says which line, for the one re-ask. */
    data class Rejection(val reason: String)

    sealed interface Outcome {
        data class Applied(val notes: Notes, val rulesPutBack: Int) : Outcome
        data class Rejected(val rejection: Rejection) : Outcome
    }

    fun apply(current: Notes, delta: NotesDelta): Outcome {
        // RULES: the old ones first, unchanged; a "new" rule that rewords an existing one is dropped.
        val rules = current.rules.toMutableList()
        for (candidate in delta.rules) {
            if (rules.none { sameRule(it, candidate) }) rules += candidate
        }

        // STATE: replace in place, append new names, `removed` deletes.
        val state = current.state.toMutableList()
        for ((name, value) in delta.changes) {
            if (value.equals("removed", ignoreCase = true)) {
                state.removeAll { it.first.equals(name, ignoreCase = true) }
                continue
            }
            val stored = resolveWorking(value) ?: return Outcome.Rejected(
                Rejection("the working of '$name: $value' does not compute — rewrite the line with the correct result"),
            )
            val at = state.indexOfFirst { it.first.equals(name, ignoreCase = true) }
            if (at >= 0) state[at] = name to stored else state += name to stored
        }

        val open = delta.open ?: current.open
        return Outcome.Applied(Notes(rules, state, open), 0)
    }

    /**
     * A value given as working (`stock: 120 + 49 = 169`) is stored as its result; the working is
     * verified first. Returns null when a `left = right` chain is false (ctxkeeper asks once more
     * in that case rather than trusting the summary). A value with no working is returned as is.
     * Only whole-number additions/subtractions are checked — anything fancier is left alone rather
     * than wrongly refused.
     */
    internal fun resolveWorking(value: String): String? {
        val eq = value.lastIndexOf('=')
        if (eq <= 0 || eq == value.length - 1) return value
        val left = value.substring(0, eq).trim()
        val right = value.substring(eq + 1).trim()
        val result = wholeNumber(right) ?: return value
        var sum: Long = 0
        var i = 0
        var sign = 1L
        var expectNumber = true
        while (i < left.length) {
            val c = left[i]
            when {
                c.isWhitespace() -> i++
                c == '+' || c == '-' -> {
                    if (expectNumber && c == '-') {
                        sign = -sign
                        i++
                    } else if (!expectNumber) {
                        sign = if (c == '+') 1L else -1L
                        expectNumber = true
                        i++
                    } else {
                        // a leading sign, then nothing but a number follows — handled by number parse
                        expectNumber = true
                        i++
                    }
                }
                c.isDigit() -> {
                    var j = i
                    while (j < left.length && left[j].isDigit()) j++
                    val n = left.substring(i, j).toLongOrNull() ?: return value
                    sum += sign * n
                    sign = 1L
                    expectNumber = false
                    i = j
                }
                else -> return value // not a plain +- chain: not our check to make
            }
        }
        if (expectNumber) return value // trailing operator: not a chain we can judge
        return if (sum == result.toLong()) value else null
    }

    private fun wholeNumber(text: String): Long? {
        val t = text.trim().removePrefix("+").removePrefix("−")
        if (t.isEmpty() || !t.all { it.isDigit() }) return null
        return t.toLongOrNull()
    }

    /**
     * Two rules are "the same rule" when the **new** candidate is nearly contained in an existing
     * one: a restatement of it (in other words, or shortened) shares ≥60% of the candidate's words
     * with it, so the candidate would only drift the original and is not added. An extension of a
     * rule (which adds the real content) shares too little of *itself* to be swallowed — that is the
     * direction ctxkeeper's `same_rule` guards, and the one a plain Jaccard similarity gets wrong.
     * A genuinely new rule shares almost nothing either way.
     */
    internal fun sameRule(existing: String, candidate: String): Boolean {
        val wOld = words(existing)
        val wNew = words(candidate)
        if (wOld.isEmpty() || wNew.isEmpty()) return false
        val shared = wNew.count { it in wOld }.toLong()
        return shared * 100 >= wNew.size * 60
    }

    private fun words(text: String): Set<String> =
        text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.toSet()
}
