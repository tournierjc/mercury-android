package io.github.tournierjc.mercury.data

import io.github.tournierjc.mercury.data.dto.DeliverableDto

/**
 * What the owner asked the deliverables list to show: a word, a kind of file, and at most one bot.
 *
 * The list is the fleet's — seventy-one files from ten bots in the owner's folder — so the two
 * questions that matter are "where is the one I wrote yesterday" and "what did this bot produce".
 * Both are answered before the list is drawn, on plain data, so the answers can be tested without a
 * screen (and so the screen has nothing to get wrong).
 */
data class DeliverableFilter(
    val query: String = "",
    val kind: OutputKind? = null,
    val botId: String? = null,
) {
    /** True when nothing narrows the list: the screen then shows its own count, not "12 of 71". */
    val isOff: Boolean get() = query.isBlank() && kind == null && botId == null
}

/**
 * Search is on the file name and the folder it sits in — never on the bot's name: filtering by bot
 * is [DeliverableFilter.botId]'s job, and a word search that silently matched ten bots would make
 * the two controls disagree about what they filtered.
 *
 * Comparing is forgiving on case and on accents: a phone keyboard is not where anybody types
 * "Vérification" with the accent in the right place, and "verif" has to find it.
 */
fun List<DeliverableDto>.filteredBy(filter: DeliverableFilter): List<DeliverableDto> {
    val needle = filter.query.foldToAscii().trim()
    return this.filter { output ->
        (filter.kind == null || BotOps.kind(output.name) == filter.kind) &&
            (filter.botId == null || output.botId == filter.botId) &&
            (needle.isEmpty() || haystack(output).contains(needle))
    }
}

/** The names a search may match, folded once per file rather than once per keystroke. */
private fun haystack(output: DeliverableDto): String =
    (output.name + " " + output.path).foldToAscii()

internal fun String.foldToAscii(): String = lowercase().map { char ->
    when (char) {
        'à', 'á', 'â', 'ã', 'ä', 'å' -> "a"
        'ç' -> "c"
        'è', 'é', 'ê', 'ë' -> "e"
        'ì', 'í', 'î', 'ï' -> "i"
        'ñ' -> "n"
        'ò', 'ó', 'ô', 'õ', 'ö' -> "o"
        'ù', 'ú', 'û', 'ü' -> "u"
        'ý', 'ÿ' -> "y"
        // Two letters, not one: « Œil » is typed "oeil" on a phone keyboard, and a fold that
        // answered "oil" would make the fleet's own bot unfindable by its name.
        'œ' -> "oe"
        'æ' -> "ae"
        else -> char.toString()
    }
}.joinToString("")

/**
 * One bot's files, ready to be drawn as a group.
 *
 * [name] falls back to the id: a bot that was removed still owns the file it wrote, and a group
 * headed by nothing is worse than one headed by an identifier.
 */
data class DeliverableGroup(
    val botId: String,
    val name: String,
    val files: List<DeliverableDto>,
)

/**
 * The groups whose bot name matches a typed fragment, forgiving case and accents like the file
 * search above them — an empty query is the whole fleet, which is what the picker opens on.
 *
 * It lives here, not in the screen, for the same reason [filteredBy] does: the rule is data, and
 * "oeil" finding « Œil » is a fact worth a test rather than a look at a phone.
 */
fun List<DeliverableGroup>.matchingBots(query: String): List<DeliverableGroup> {
    val needle = query.foldToAscii().trim()
    return if (needle.isEmpty()) this else filter { it.name.foldToAscii().contains(needle) }
}

/**
 * Groups in the order the owner will look for them: by name, with the bots the roster no longer
 * knows last — they are history, not the place work is happening.
 */
fun List<DeliverableDto>.groupedByBot(names: Map<String, String>): List<DeliverableGroup> =
    groupBy { it.botId }
        .map { (botId, files) ->
            DeliverableGroup(
                botId = botId,
                name = names[botId]?.ifBlank { botId } ?: botId.ifBlank { "—" },
                files = files.sortedByDescending { it.mtimeMs },
            )
        }
        .sortedWith(compareBy({ names[it.botId] == null }, { it.name.lowercase() }))
