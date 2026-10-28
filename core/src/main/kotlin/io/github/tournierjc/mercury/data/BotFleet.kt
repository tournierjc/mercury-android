package io.github.tournierjc.mercury.data

import kotlinx.serialization.json.JsonObject

/**
 * The roster as a tree: a lead and the crew it runs, then everyone who stands alone.
 *
 * The bots list used to be flat, with the only hint of hierarchy buried in a subtitle line
 * ("crew de mac-lead"). That reads as a label, not as a structure: finding the five members of a
 * fleet meant scanning every row. A lead and its crew are one row that opens (SPECS/109 §roster).
 */
sealed interface FleetRow {
    /** A bot nobody leads and that leads nobody: one row, no disclosure. */
    data class Solo(val id: String) : FleetRow

    /**
     * A lead. [crewIds] is empty for a lead that has not spawned anyone yet — the row is still a
     * lead (crown, badge) but there is nothing to open.
     */
    data class Lead(val id: String, val crewIds: List<String>) : FleetRow
}

/**
 * Groups [bots] into [FleetRow]s.
 *
 * - The order of the top level and of each crew is the order the server sent: the roster is the
 *   owner's mental list, and re-sorting it by name would move a bot under his thumb.
 * - A bot whose `parent` names a bot that is not in the list stays solo: nesting it under a lead
 *   that is not there would hide it behind a disclosure arrow that leads to nowhere.
 * - A lead keeps its own row (settings, conversation) — the crew hangs under it, it is not
 *   replaced by it.
 */
fun fleetRows(bots: List<JsonObject>): List<FleetRow> {
    val ids = bots.map { BotJson.id(it) }.toSet()
    val crewByLead = bots
        .mapNotNull { bot ->
            val parent = BotJson.parent(bot)
            if (parent.isBlank() || parent !in ids) null else parent to BotJson.id(bot)
        }
        .groupBy({ it.first }, { it.second })

    val nested = crewByLead.values.flatten().toSet()
    return bots
        .filter { BotJson.id(it) !in nested }
        .map { bot ->
            val id = BotJson.id(bot)
            val crew = crewByLead[id].orEmpty()
            if (BotJson.isLead(bot)) FleetRow.Lead(id, crew) else FleetRow.Solo(id)
        }
}
