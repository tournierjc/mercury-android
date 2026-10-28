package io.github.tournierjc.mercury.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Something about a bot that the owner should know without opening the app screen.
 * Produced by diffing two rosters, so a notification is raised once per transition
 * and never re-raised while the state stays the same.
 */
data class BotAttention(
    val botId: String,
    val botName: String,
    val kind: Kind,
    val detail: String = "",
) {
    enum class Kind { NeedsYou, RunFinished, RunFailed }
}

/**
 * Accessors for Mercury's bot roster.
 *
 * `GET /api/bots` answers `{bots:[…], queue:{pending,claimed,dlq}, available}` and each
 * entry is a status summary: `{id,name,enabled,state,activity,lastRunAt,lastRunState,
 * needsYou,fleetRole,parent,crewWorking,routinePausedUntil}`. Manifests (from
 * `GET /api/bots/:id`) carry more (`description`, `model`, `tier`, `autonomy`…), and the
 * accessors here read both shapes.
 */
object BotJson {

    fun roster(root: JsonObject?): List<JsonObject> =
        (root?.get("bots") as? JsonArray)?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
            ?: emptyList()

    fun id(bot: JsonObject?): String = string(bot, "id")

    fun name(bot: JsonObject?): String = string(bot, "name").ifBlank { id(bot) }

    fun description(bot: JsonObject?): String = string(bot, "description")

    /**
     * The model a bot pins, as the daemon stores it: `{ provider, model }`, not a string.
     *
     * `string(bot, "model")` returned "" on that object, so every bot read as « défaut » and the
     * sheet's text field wrote a string where the manifest keeps an object.
     */
    fun modelProvider(bot: JsonObject?): String =
        ((bot?.get("model") as? JsonObject)?.get("provider") as? JsonPrimitive)?.contentOrNull.orEmpty()

    fun modelName(bot: JsonObject?): String =
        ((bot?.get("model") as? JsonObject)?.get("model") as? JsonPrimitive)?.contentOrNull.orEmpty()

    /** Display form: the provider, then the model slug when one is pinned. */
    fun model(bot: JsonObject?): String =
        listOf(modelProvider(bot), modelName(bot)).filter { it.isNotBlank() }.joinToString(" · ")

    fun tier(bot: JsonObject?): String = string(bot, "tier")

    /** `idle`, `running`, `queued`, `paused` or `disabled`. */
    fun state(bot: JsonObject?): String = string(bot, "state").ifBlank { "idle" }

    /** The live label of the current step ("Reading /work/report.md"…), empty when idle. */
    fun activity(bot: JsonObject?): String = string(bot, "activity")

    fun needsYou(bot: JsonObject?): Boolean = bool(bot, "needsYou")

    fun enabled(bot: JsonObject?): Boolean = bot?.get("enabled")?.let { it is JsonPrimitive && (it.booleanOrNull ?: true) } ?: true

    fun fleetRole(bot: JsonObject?): String = string(bot, "fleetRole")

    fun parent(bot: JsonObject?): String = string(bot, "parent")

    fun isLead(bot: JsonObject?): Boolean = fleetRole(bot) == "lead"

    fun crewWorking(bot: JsonObject?): Int? = (bot?.get("crewWorking") as? JsonPrimitive)?.intOrNull

    fun lastRunAt(bot: JsonObject?): String = string(bot, "lastRunAt")

    fun lastRunState(bot: JsonObject?): String = string(bot, "lastRunState")

    /**
     * Two letters for the avatar. Mercury has no avatar field at all, so the app draws a
     * deterministic monogram rather than pretending to show a picture.
     */
    /**
     * The roster says whether a custom face exists, so a client does not probe one URL
     * per bot. Absent means false: an older Mercury without the field has no avatars.
     */
    fun hasAvatar(node: JsonObject?): Boolean =
        (node?.get("hasAvatar") as? JsonPrimitive)?.booleanOrNull == true

    fun monogram(bot: JsonObject?): String {
        val source = name(bot).ifBlank { id(bot) }
        val words = source.split(' ', '-', '_').filter { it.isNotBlank() }
        val letters = when {
            words.size >= 2 -> words.take(2).map { it.first() }
            source.isNotBlank() -> source.take(2).toList()
            else -> listOf('?')
        }
        return letters.joinToString("").uppercase()
    }

    /** Stable hue (0…359) derived from the id, so a bot keeps its colour across sessions. */
    fun hue(bot: JsonObject?): Int {
        val source = id(bot).ifBlank { name(bot) }
        if (source.isBlank()) return 210
        var hash = 0
        for (char in source) hash = (hash * 31 + char.code) and 0x7fffffff
        return hash % 360
    }

    /** The roster chip's word, as a key — the screens own both languages. */
    fun stateNotice(state: String): Notice = when (state) {
        "running" -> Notice(NoticeKey.RUN_RUNNING_STATE)
        "queued" -> Notice(NoticeKey.RUN_QUEUED_STATE)
        "paused" -> Notice(NoticeKey.BOT_STATE_PAUSED)
        "disabled" -> Notice(NoticeKey.BOT_STATE_DISABLED)
        else -> Notice(NoticeKey.BOT_STATE_RESTING)
    }.asInfo()

    /**
     * Compares two rosters and returns what changed in a way worth a notification:
     * a bot that now needs the owner, and runs that just finished (or failed).
     */
    fun attention(before: List<JsonObject>, after: List<JsonObject>): List<BotAttention> {
        val previous = before.associateBy { id(it) }
        val result = ArrayList<BotAttention>()
        for (bot in after) {
            val botId = id(bot)
            val old = previous[botId]
            if (needsYou(bot) && !needsYou(old)) {
                result += BotAttention(botId, name(bot), BotAttention.Kind.NeedsYou, activity(bot))
            }
            val previousRun = lastRunState(old)
            val currentRun = lastRunState(bot)
            if (currentRun.isNotBlank() && currentRun != previousRun) {
                when (currentRun.lowercase()) {
                    "done", "completed", "success", "ok" ->
                        result += BotAttention(botId, name(bot), BotAttention.Kind.RunFinished, activity(bot))
                    "failed", "error", "cancelled", "timeout" ->
                        result += BotAttention(botId, name(bot), BotAttention.Kind.RunFailed, activity(bot))
                }
            }
        }
        return result
    }

    /**
     * What the roster says needs the owner, read without any history. Unlike
     * [attention] this cannot see a transition, so it only reports states that are
     * meaningful on their own: a bot waiting on an answer. Run outcomes need the diff
     * and stay out — otherwise every poll would re-announce yesterday's finished run.
     */
    fun currentAttention(bots: List<JsonObject>): List<BotAttention> =
        bots.filter { needsYou(it) }
            .map { BotAttention(id(it), name(it), BotAttention.Kind.NeedsYou, activity(it)) }

    /**
     * A stable identity for "this alert has been raised". A background check that runs
     * every fifteen minutes must not re-announce the same pending question, while a new
     * question from the same bot must get through: hence the detail in the key.
     */
    fun attentionKey(attention: BotAttention): String =
        "${attention.botId}|${attention.kind}|${attention.detail}"

    private fun string(node: JsonObject?, key: String): String =
        (node?.get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun bool(node: JsonObject?, key: String): Boolean =
        (node?.get(key) as? JsonPrimitive)?.booleanOrNull == true
}
