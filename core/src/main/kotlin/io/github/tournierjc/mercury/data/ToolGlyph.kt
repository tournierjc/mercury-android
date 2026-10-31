package io.github.tournierjc.mercury.data

/**
 * One tool call, read as a line of the trace the way a terminal conversation shows it: an emoji
 * for the kind of call, the tool's name, and its argument drawn as the command it ran.
 *
 * The owner reads these lines on a phone, in the middle of an answer, and a turn that ran twenty
 * commands is only legible if each call says *what kind* of thing it was at a glance — a shell
 * command, a file read, a skill, a delivery. The name alone ("run_command", "edit_file") is a
 * vocabulary, not a picture; the emoji is the picture, and the argument is the detail.
 */
data class TraceLine(
    /** The call's kind, as one emoji. */
    val glyph: String,
    /** The tool's name, as the daemon spells it. */
    val name: String,
    /** The command / path / target it ran, ready to draw in a code block. Never the name again. */
    val detail: String,
    /** The outcome when the daemon gave one (`42 lines`, `✗ file not found`). */
    val outcome: String? = null,
    val failed: Boolean = false,
)

object ToolGlyph {

    /** How a step looks in the trace: emoji, name, argument, outcome. */
    fun lineOf(step: ToolStep): TraceLine {
        val name = step.tool.trim().removePrefix(MARK_FAILED).removePrefix(MARK_OK).trim()
            .ifBlank { FALLBACK_NAME }
        val detail = detailOf(name, step.label)
        val outcome = step.summary?.trim()?.takeIf { it.isNotEmpty() }
        val failed = step.failed ||
            step.label.trimStart().startsWith(MARK_FAILED) ||
            outcome?.startsWith(MARK_FAILED) == true
        return TraceLine(
            glyph = of(name),
            name = name,
            detail = detail,
            outcome = outcome,
            failed = failed,
        )
    }

    /**
     * The emoji of a tool, by family.
     *
     * Keyed on the tool's name rather than on a registry the client does not have: the daemon's
     * tool list grows (`fleet_delegate`, `bot_deliver`, plugins), and a call that has no emoji of
     * its own must still get one — a generic wrench says "a tool ran", which is never wrong, while
     * an empty column would look like a rendering bug.
     */
    fun of(tool: String): String {
        val name = tool.trim().lowercase()
        return when {
            name.isBlank() -> FALLBACK
            name == "…" || name.startsWith("…") -> "⋯"
            name.startsWith("run_command") || name.startsWith("command") ||
                name == "exec" || name == "shell" || name == "bash" || name == "terminal" ||
                name == "cd" || name == "awk" || name == "sed" || name == "grep" -> "💻"
            name.startsWith("git") -> "🌿"
            name.startsWith("list_dir") || name.startsWith("ls") || name.startsWith("find") -> "📁"
            name.startsWith("read_file") || name.startsWith("read") || name.startsWith("cat") -> "📄"
            name.startsWith("create_file") || name.startsWith("write_file") || name.startsWith("write") -> "📝"
            name.startsWith("edit_file") || name.startsWith("edit") || name.startsWith("patch") -> "✏️"
            name.startsWith("web_search") || name.startsWith("search") || name.startsWith("grep_web") -> "🔎"
            name.startsWith("fetch") || name.startsWith("http") || name.startsWith("url") -> "🌐"
            name.startsWith("use_skill") || name.startsWith("skill") -> "📚"
            name.startsWith("fleet") -> "👥"
            name.startsWith("bot_deliver") || name.startsWith("deliver") -> "📦"
            name.startsWith("bot_send") || name.startsWith("send") || name.startsWith("reply") -> "💬"
            name.startsWith("bot_state") || name.startsWith("state") || name.startsWith("memory") -> "🧠"
            name.startsWith("bot_schedule") || name.startsWith("schedule") || name.startsWith("cron") -> "🗓️"
            name.startsWith("bot_spawn") || name.startsWith("spawn") || name.startsWith("bot_") -> "🤖"
            name.startsWith("plan") || name.startsWith("todo") -> "🗒️"
            else -> FALLBACK
        }
    }

    /**
     * The argument, as a command to read.
     *
     * Two shapes reach the phone. A live step's label is `tool(k=v, k2=v2)` — the daemon's own
     * rendering of the call; a finished one (journal summary, run transcript) carries the argument
     * alone, or nothing. Both must end up as the command, never as the tool's name repeated: that
     * repetition was exactly what made a trace look like it narrated itself.
     */
    private fun detailOf(name: String, label: String): String {
        val raw = label.trim().removePrefix(MARK_FAILED).removePrefix(MARK_OK).trim()
        if (raw.isEmpty() || raw == name) return ""
        val prefix = "$name("
        // A live step's `run_command(command=ls -a)`: the call's arguments, not its name.
        if (raw.startsWith(prefix) && raw.endsWith(")")) {
            return stripCommandKey(raw.substring(prefix.length, raw.length - 1).trim())
        }
        return stripCommandKey(raw)
    }

    /**
     * `command=ls -a` → `ls -a`.
     *
     * A code block *is* the command line, so the key that names it adds nothing — and with its
     * quotes it reads like a broken shell line. Only that one key is dropped: `path=…` or
     * `target=…` say which argument this is, and they stay.
     */
    private fun stripCommandKey(detail: String): String {
        if (!detail.startsWith("command=")) return detail
        return detail.removePrefix("command=").trim().removeSurrounding("\"")
    }

    private const val MARK_FAILED = "✗"
    private const val MARK_OK = "✓"
    private const val FALLBACK = "🔧"
    private const val FALLBACK_NAME = "outil"
}
