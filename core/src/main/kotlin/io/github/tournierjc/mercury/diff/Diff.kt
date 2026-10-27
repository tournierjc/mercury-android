package io.github.tournierjc.mercury.diff

/**
 * A unified diff, read the way the dashboard's `diff-view` renderer reads one.
 *
 * The contract is deliberately tiny (see `mercury-plugin-diff-view/README.md`): the core
 * knows nothing about diffs, a renderer claims the ```diff fence and owns everything after
 * it. Two clients claiming the same fence must classify the same lines the same way, or the
 * same answer looks different on the phone and in the browser — hence this file, which is a
 * line-for-line port of the classification in `renderer/diff.html`.
 */
data class DiffLine(val kind: Kind, val text: String) {

    enum class Kind {
        /** `diff `, `index `, `---`/`+++` headers: file coordinates, not content. */
        META,

        /** `@@ -1,4 +1,6 @@`: the hunk header. */
        HUNK,

        /** A line only the new file has; [text] has the leading `+` removed. */
        ADD,

        /** A line only the old file has; [text] has the leading `-` removed. */
        DEL,

        /** Unchanged context. */
        CONTEXT,
    }
}

data class DiffDocument(
    val lines: List<DiffLine> = emptyList(),
    val added: Int = 0,
    val removed: Int = 0,
)

object Diff {

    const val FENCE = "diff"

    /**
     * Parse a fenced patch. Never refuses: a diff is text, and the worst case of drawing a
     * half-written one is a wrong-coloured line — not a lost message. That is also why the
     * renderer works mid-stream with no partial mode of its own.
     */
    fun parse(text: String): DiffDocument {
        val body = text.trimEnd('\n').trimEnd('\r')
        if (body.isBlank()) return DiffDocument()
        val lines = mutableListOf<DiffLine>()
        var added = 0
        var removed = 0
        for (raw in body.split('\n')) {
            val line = raw.trimEnd('\r')
            // Order matters: `+++`/`---` are file headers, not an added and a removed line.
            val kind = when {
                line.startsWith("+++") || line.startsWith("---") ||
                    line.startsWith("diff ") || line.startsWith("index ") -> DiffLine.Kind.META
                line.startsWith("+") -> DiffLine.Kind.ADD
                line.startsWith("-") -> DiffLine.Kind.DEL
                line.startsWith("@@") -> DiffLine.Kind.HUNK
                else -> DiffLine.Kind.CONTEXT
            }
            when (kind) {
                DiffLine.Kind.ADD -> {
                    added++
                    lines += DiffLine(kind, line.drop(1))
                }
                DiffLine.Kind.DEL -> {
                    removed++
                    lines += DiffLine(kind, line.drop(1))
                }
                else -> lines += DiffLine(kind, line)
            }
        }
        return DiffDocument(lines = lines, added = added, removed = removed)
    }
}
