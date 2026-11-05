package io.github.tournierjc.mercury.bots

import io.github.tournierjc.mercury.context.ContextKeeper
import io.github.tournierjc.mercury.context.ContextTurn
import io.github.tournierjc.mercury.context.CutReport
import io.github.tournierjc.mercury.context.NotesDelta
import io.github.tournierjc.mercury.context.SkipReason

/**
 * The bot thread as the context keeper sees it.
 *
 * The keeper works on [ContextTurn]s; the thread lives as [BotMessage]s. This maps one to the other
 * without inventing anything: a bubble's text is its text, the owner's bubble is the user role, the
 * bot's is the assistant role. The mapping is a pure function so the compression path — including
 * the "a cut must always shrink the view" gate — runs in a JVM test on a real thread built by
 * [BotThread], not on a synthetic list.
 */
object BotThreadCompression {

    /**
     * The thread as keeper turns.
     *
     * A system turn leads the conversation the way ctxkeeper expects: the daemon's persona never
     * reaches this list (the app does not render it), so the opening is the first owner message —
     * which is exactly the ctxkeeper rule "the opening messages stay verbatim", applied to what the
     * app actually has.
     */
    fun turnsOf(messages: List<BotMessage>): List<ContextTurn> =
        messages.filter { it.text.isNotBlank() }.mapIndexed { i, m ->
            ContextTurn(
                number = i,
                role = if (m.fromBot) ContextTurn.Role.Assistant else ContextTurn.Role.User,
                text = m.text,
            )
        }

    /**
     * The conversation the chat screen should draw after compression.
     *
     * The whole journal is asked through the keeper; the result is either the thread unchanged
     * (`Fits`/`TooShort` — the screen must never see a gap it cannot explain) or the kept view with
     * the pinned notes as a system-role bubble the screen can render like any other. The caller
     * draws the archive notice only when the outcome says a cut actually ran.
     */
    fun compressedThread(
        messages: List<BotMessage>,
        keeper: ContextKeeper,
    ): Pair<List<BotMessage>, KeepOutcomeView> {
        val turns = turnsOf(messages)
        val outcome = keeper.maybeCompress(turns, writer = null)
        if (outcome.skip != SkipReason.Cut) return messages to KeepOutcomeView(outcome.skip, null)
        val view = keeper.viewFor(turns)
        val rendered = view.map { turn ->
            BotMessage(
                key = "ctx-${turn.number}",
                fromBot = turn.role == ContextTurn.Role.Assistant,
                text = turn.text,
                at = 0L,
                // The pinned notes are keeper material, not anyone's message: the screen draws them
                // as a context block, never as a bubble from the bot.
                state = if (turn.role == ContextTurn.Role.System && turn.number < 0) NOTES else "",
            )
        }
        return rendered to KeepOutcomeView(SkipReason.Cut, outcome.report)
    }

    /** The marker the screen keys the notes block on. */
    const val NOTES = "notes"

    /** What the screen needs to know about the attempt: what happened, and the numbers it can show. */
    data class KeepOutcomeView(
        val skip: SkipReason,
        val report: CutReport? = null,
    )

    /** A notes writer that never asks the model: the screen uses it to *measure*, not to cut. */
    val measureOnly: io.github.tournierjc.mercury.context.NotesWriter =
        io.github.tournierjc.mercury.context.NotesWriter { _, _ -> null }

    /** The delta the model writes between the turns leaving — parsed straight from its reply. */
    fun parseDelta(reply: String): NotesDelta? = NotesDelta.parse(reply)
}
