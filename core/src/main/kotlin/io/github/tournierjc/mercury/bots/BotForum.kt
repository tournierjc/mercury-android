package io.github.tournierjc.mercury.bots

import io.github.tournierjc.mercury.data.BotJson
import io.github.tournierjc.mercury.data.dto.BotRunDto
import kotlinx.serialization.json.JsonObject

/**
 * The sessions forum: every teammate's conversation, newest activity first.
 *
 * A bot's conversation is not a session in the daemon's store — its turns run outside the session
 * repository, and the thread is rebuilt from the journal and the run transcripts. The forum therefore
 * reads the same source the conversation screen does: one journal read per bot (`?limit=1`), the
 * newest record of each, joined with the roster the live feed already holds.
 *
 * Pure, so the order and the completeness of the list can be asserted without a screen: the roster
 * is the owner's mental list and the journal decides what is new — a bot that has never run and a
 * bot the journal no longer knows are both here, because opening the conversation is always allowed.
 */
object BotForum {

    /** One row of the forum. */
    data class Row(
        /** The bot's id — opening the conversation is [Row.id] handed to `openConversation`. */
        val id: String,
        /** The bot's roster entry, as the live feed holds it (name, state, face). */
        val bot: JsonObject,
        /** The newest record the journal holds for this bot, or null when it has never run. */
        val newest: BotRunDto?,
    )

    /**
     * The forum's rows, in the order they are drawn.
     *
     * The roster's order is kept for bots that are equally quiet (no journal line, or one the
     * journal cut away): re-sorting by name would move a teammate under the owner's thumb. Bots
     * with a newest record rise above the quiet ones — the forum reads top-down as "the last
     * thing that happened", like the sessions screen reads its threads.
     */
    fun rows(bots: List<JsonObject>, journal: Map<String, List<BotRunDto>>): List<Row> {
        val entries = bots.mapNotNull { bot ->
            val id = BotJson.id(bot)
            if (id.isBlank()) return@mapNotNull null
            Row(id, bot, journal[id].orEmpty().lastOrNull { it.runId.isNotBlank() })
        }
        // Newest first where the journal speaks; the roster's order settles the ties, and a row
        // whose record lacks a start time (an older daemon) sits with the quiet ones.
        return entries
            .mapIndexed { index, row -> index to row }
            .sortedWith(compareByDescending<Pair<Int, Row>> { it.second.newest?.startedAt ?: 0L })
            .map { it.second }
    }

    /**
     * The ids the forum's journal reads cover, for a caller that reloads when the roster changes.
     *
     * Same set as [rows], without building rows: the polling and the rows builder must agree on
     * what a "session" is, and one function saying it keeps them honest.
     */
    fun ids(bots: List<JsonObject>): List<String> =
        bots.mapNotNull { BotJson.id(it).takeIf { id -> id.isNotBlank() } }
}
