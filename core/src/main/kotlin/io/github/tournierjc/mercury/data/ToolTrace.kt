package io.github.tournierjc.mercury.data

/**
 * The daemon's end-of-turn summary of what the agent called, read back from the session.
 *
 * A turn that used tools leaves one entry in the session, `kind: "tool-call"`, whose content is
 * written for a *model* to read:
 *
 * ```
 * [Tool activity in my previous turn]
 * - run_command command="ls -a /work" → ok (4 lines)
 * - grep path="/work/src" → ok (30 lines)
 * - read_file path="x" → ✗ file not found
 * ```
 *
 * Text is the right shape in the context window and the wrong shape on a screen: reopened, the
 * conversation showed that block verbatim in a bubble — a wall of dashes and arrows where the live
 * turn had shown neat chips. Parsing it back gives the phone the same vocabulary for the same facts.
 */
object ToolTrace {

    const val HEADER = "[Tool activity in my previous turn]"

    private const val ARROW = " → "

    /** True when a stored message is one of these summaries, whatever its `kind` says. */
    fun looksLike(text: String): Boolean = text.trimStart().startsWith(HEADER)

    /**
     * The steps of a summary, in the order they ran.
     *
     * `- tool args → outcome` is the daemon's line; anything else (the header, the "… N more tool
     * calls" tail, a stray blank) is kept as text only when it says something — the tail becomes a
     * step of its own so the count is not silently lost.
     */
    fun parse(text: String): List<ToolStep> {
        val steps = ArrayList<ToolStep>()
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith(HEADER) || trimmed == HEADER) continue
            if (!trimmed.startsWith("- ")) continue
            val body = trimmed.removePrefix("- ").trim()
            if (body.isBlank()) continue
            when {
                // `- … 12 more tool calls`: the summary's own tail.
                body.startsWith("…") -> steps += ToolStep(
                    tool = "…",
                    label = body.removePrefix("…").trim(),
                )

                else -> {
                    val cut = body.indexOf(ARROW)
                    val call = if (cut >= 0) body.substring(0, cut) else body
                    val outcome = if (cut >= 0) body.substring(cut + ARROW.length).trim() else ""
                    val tool = call.substringBefore(' ').trim()
                    val args = call.substringAfter(' ', "").trim()
                    steps += ToolStep(
                        tool = tool.ifBlank { "outil" },
                        label = args.ifBlank { tool },
                        summary = outcome.ifBlank { null },
                    )
                }
            }
        }
        return steps
    }
}
