package io.github.tournierjc.mercury.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * The cron routines a bot's manifest carries — `schedules` on the daemon side — as the phone
 * edits them.
 *
 * `PATCH /api/bots/:id` replaces the whole array, so sending one entry deletes every other
 * routine: exactly the shape of bug [BotAutonomy] exists to prevent for the limits. Every edit
 * here is therefore rebuilt from the list the sheet already holds, and it is plain data, so the
 * rule can be tested without a daemon.
 */
object BotRoutines {

    /** The manifest key. Named, because the ViewModel writes it into a patch body. */
    const val KEY = "schedules"

    const val NAME = "name"
    const val CRON = "cron"
    const val PROMPT = "prompt"
    const val DELIVER = "deliver"
    const val GATE_SCRIPT = "gateScript"
    const val EXPECTS = "expects"
    const val MIN_INTERVAL = "minIntervalMinutes"

    const val DELIVER_BOT_CHAT = "bot-chat"
    const val DELIVER_TELEGRAM = "telegram"
    const val DELIVER_NONE = "none"

    const val EXPECTS_WORK = "work"
    const val EXPECTS_CHECK = "check"

    /** The values the daemon accepts, in the order the form offers them. */
    val delivers: List<String> = listOf(DELIVER_BOT_CHAT, DELIVER_TELEGRAM, DELIVER_NONE)
    val allExpects: List<String> = listOf(EXPECTS_WORK, EXPECTS_CHECK)

    /** One routine, typed. The sheet edits this; [entry] turns it back into manifest JSON. */
    data class Routine(
        val name: String,
        val cron: String,
        val prompt: String,
        val deliver: String = DELIVER_BOT_CHAT,
        val expects: String = EXPECTS_WORK,
        /** Below this, a cron that fires again is skipped instead of piling a second run up. */
        val minIntervalMinutes: Int? = null,
        /** A script the daemon runs first; when it fails the routine does not run. */
        val gateScript: String? = null,
    )

    /**
     * The routines the manifest holds, in stored order.
     *
     * A manifest without `schedules` — every bot made before routines existed — reads as an empty
     * list, not as an error: an empty section is a real state the screen explains.
     */
    fun list(manifest: JsonObject?): List<JsonObject> =
        (manifest?.get(KEY) as? JsonArray)
            ?.mapNotNull { entry -> runCatching { entry as JsonObject }.getOrNull() }
            .orEmpty()

    /** One stored entry, read the way the form needs it. Unknown values are kept, not rewritten. */
    fun read(routine: JsonObject): Routine = Routine(
        name = text(routine, NAME),
        cron = text(routine, CRON),
        prompt = text(routine, PROMPT),
        deliver = text(routine, DELIVER).ifBlank { DELIVER_BOT_CHAT },
        expects = text(routine, EXPECTS).ifBlank { EXPECTS_WORK },
        minIntervalMinutes = (routine[MIN_INTERVAL] as? JsonPrimitive)?.intOrNull,
        gateScript = text(routine, GATE_SCRIPT).ifBlank { null },
    )

    /** The routine's own name — the key every edit is matched on. */
    fun name(routine: JsonObject): String = text(routine, NAME)

    /**
     * [current] with [routine] added, or replacing the entry that carries the same name.
     *
     * The name is the identity and is matched case-insensitively: a form that only changes the
     * case of a word is still editing the same routine, not creating a second one beside it. The
     * replacement keeps the original position, so saving does not reshuffle the list under the
     * finger that just tapped « Enregistrer ».
     *
     * An invalid routine leaves the list untouched; [isValid] is what callers check first.
     */
    fun upsert(current: List<JsonObject>, routine: Routine): List<JsonObject> {
        if (!isValid(routine)) return current
        val stored = entry(routine)
        val index = current.indexOfFirst { name(it).equals(routine.name.trim(), ignoreCase = true) }
        return if (index < 0) current + stored else current.toMutableList().also { it[index] = stored }
    }

    /** Drops the routine called [routineName] — same case-insensitive match as [upsert]. */
    fun remove(current: List<JsonObject>, routineName: String): List<JsonObject> =
        current.filterNot { name(it).equals(routineName.trim(), ignoreCase = true) }

    /** The list as the array a patch body carries. */
    fun serialize(routines: List<JsonObject>): JsonArray = JsonArray(routines)

    /**
     * A five-field cron (node-cron): minute, hour, day of month, month, day of week.
     *
     * Only the characters a schedule is written with are accepted — digits, `*`, `/`, `-` and `,`.
     * The point is not to guess node-cron's semantics but to refuse a typo before it reaches the
     * daemon, where a schedule that never fires fails in silence.
     */
    fun isValidCron(expression: String): Boolean {
        val fields = expression.trim().split(Regex("\\s+"))
        return fields.size == 5 && fields.all { it.isNotEmpty() && CRON_FIELD.matches(it) }
    }

    /** What every write is allowed to store: a name to match on, a cron to fire, a prompt to run. */
    fun isValid(routine: Routine): Boolean =
        routine.name.isNotBlank() && routine.prompt.isNotBlank() && isValidCron(routine.cron)

    /**
     * The manifest entry, with keys in the daemon's own order.
     *
     * `gateScript` and `minIntervalMinutes` are left out rather than written empty: absence is how
     * the daemon spells "nothing", and an empty string would be a gate script that always fails.
     */
    private fun entry(routine: Routine): JsonObject = buildJsonObject {
        put(NAME, routine.name.trim())
        put(CRON, routine.cron.trim())
        put(PROMPT, routine.prompt.trim())
        put(DELIVER, routine.deliver.ifBlank { DELIVER_BOT_CHAT })
        routine.gateScript?.trim()?.takeIf { it.isNotEmpty() }?.let { put(GATE_SCRIPT, it) }
        put(EXPECTS, routine.expects.ifBlank { EXPECTS_WORK })
        routine.minIntervalMinutes?.takeIf { it > 0 }?.let { put(MIN_INTERVAL, it) }
    }

    private fun text(node: JsonObject, key: String): String =
        (node[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private val CRON_FIELD = Regex("[0-9*/,-]+")
}
