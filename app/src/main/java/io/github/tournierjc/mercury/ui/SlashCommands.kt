package io.github.tournierjc.mercury.ui

import androidx.annotation.StringRes
import io.github.tournierjc.mercury.R

/**
 * The slash commands the daemon understands, as the phone offers them.
 *
 * The list is the web app's palette (`ui/src/pages/Chat.tsx`) and the daemon's own manual, phrased
 * for this screen: the command itself is what gets sent, so it is never translated, while what it
 * does is. Tapping one writes it into the field rather than sending it — half of these take an
 * argument (`/models use …`, `/bg killall`), and a palette that sends `/bg` alone would start
 * nothing and say nothing.
 */
data class SlashCommand(
    /** What is typed: `/help`, `/bg list`. */
    val command: String,
    @StringRes val description: Int,
    @StringRes val category: Int,
)

object SlashCommands {

    private val GENERAL = R.string.slash_cat_general
    private val AGENTS = R.string.slash_cat_agents
    private val BACKGROUND = R.string.slash_cat_background
    private val MODELS = R.string.slash_cat_models
    private val CODE = R.string.slash_cat_code
    private val SPOTIFY = R.string.slash_cat_spotify
    private val BUDGET = R.string.slash_cat_budget
    private val MEMORY = R.string.slash_cat_memory
    private val WORKSPACE = R.string.slash_cat_workspace

    val all: List<SlashCommand> = listOf(
        SlashCommand("/help", R.string.slash_help, GENERAL),
        SlashCommand("/progress", R.string.slash_progress, GENERAL),
        SlashCommand("/halt", R.string.slash_halt, GENERAL),
        SlashCommand("/stop", R.string.slash_stop, GENERAL),
        SlashCommand("/permissions", R.string.slash_permissions, GENERAL),
        SlashCommand("/exit", R.string.slash_exit, GENERAL),
        SlashCommand("/agents", R.string.slash_agents, AGENTS),
        SlashCommand("/agents kill", R.string.slash_agents_kill, AGENTS),
        SlashCommand("/bg", R.string.slash_bg, BACKGROUND),
        SlashCommand("/bg:", R.string.slash_bg_delegate, BACKGROUND),
        SlashCommand("/bg list", R.string.slash_bg_list, BACKGROUND),
        SlashCommand("/bg current", R.string.slash_bg_current, BACKGROUND),
        SlashCommand("/bg stop", R.string.slash_bg_stop, BACKGROUND),
        SlashCommand("/bg killall", R.string.slash_bg_killall, BACKGROUND),
        SlashCommand("/bg clear", R.string.slash_bg_clear, BACKGROUND),
        SlashCommand("/models", R.string.slash_models, MODELS),
        SlashCommand("/models use", R.string.slash_models_use, MODELS),
        SlashCommand("/code", R.string.slash_code, CODE),
        SlashCommand("/code plan", R.string.slash_code_plan, CODE),
        SlashCommand("/code execute", R.string.slash_code_execute, CODE),
        SlashCommand("/code off", R.string.slash_code_off, CODE),
        SlashCommand("/spotify", R.string.slash_spotify, SPOTIFY),
        SlashCommand("/spotify play", R.string.slash_spotify_play, SPOTIFY),
        SlashCommand("/spotify pause", R.string.slash_spotify_pause, SPOTIFY),
        SlashCommand("/spotify next", R.string.slash_spotify_next, SPOTIFY),
        SlashCommand("/spotify prev", R.string.slash_spotify_prev, SPOTIFY),
        SlashCommand("/spotify queue", R.string.slash_spotify_queue, SPOTIFY),
        SlashCommand("/budget", R.string.slash_budget, BUDGET),
        SlashCommand("/budget override", R.string.slash_budget_override, BUDGET),
        SlashCommand("/budget reset", R.string.slash_budget_reset, BUDGET),
        SlashCommand("/budget set", R.string.slash_budget_set, BUDGET),
        SlashCommand("/memory", R.string.slash_memory, MEMORY),
        SlashCommand("/ws", R.string.slash_ws, WORKSPACE),
    )

    /**
     * What matches a query typed in the palette.
     *
     * Matches on the command alone: `/bg` must offer `/bg list` even though its description says
     * "tâche de fond" and its category "Arrière-plan" — the owner is typing a command, not looking
     * for a word. A blank query offers everything, in the catalogue's order.
     */
    fun matching(query: String, catalogue: List<SlashCommand> = all): List<SlashCommand> {
        val trimmed = query.trim().removePrefix("/").lowercase()
        if (trimmed.isBlank()) return catalogue
        return catalogue.filter { it.command.removePrefix("/").lowercase().contains(trimmed) }
    }

    /**
     * The field's new text once a command is picked.
     *
     * A `/xxx` already typed at the end is replaced instead of appended — picking `/bg list` after
     * typing `/bg` must not leave `/bg /bg list`. The command is followed by a space so the argument
     * some of these need can be typed straight away.
     */
    fun insert(current: String, command: String): String {
        val head = current.trimEnd()
        // The word being typed: a trailing `/bg` is what the pick replaces, not something to keep.
        val lastWord = head.substringAfterLast(' ', head)
        val base = if (lastWord.startsWith("/")) head.dropLast(lastWord.length).trimEnd() else head
        return if (base.isBlank()) "$command " else "$base $command "
    }
}
