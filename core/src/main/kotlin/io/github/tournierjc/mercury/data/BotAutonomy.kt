package io.github.tournierjc.mercury.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A bot's autonomy limits, as the manifest stores them and as the phone edits them.
 *
 * The daemon's `PATCH /api/bots/:id` replaces the whole `autonomy` object when it is present:
 * sending `{"autonomy":{"dailyTokenBudget":500000}}` therefore **erases** the step budget, the
 * per-turn token cap and the turn's wall clock. The phone used to send exactly that, one key at a
 * time. Edits are merged from the manifest the screen already holds, and this is where the merge is
 * written — plain data, so the rule can be tested without a daemon.
 */
object BotAutonomy {

    /** The keys this app edits, in the order the sheet shows them. */
    const val STEPS = "maxSteps"
    const val TURN_MINUTES = "maxTurnMinutes"
    const val TURN_TOKENS = "maxTokensPerTurn"
    const val DAILY_TOKENS = "dailyTokenBudget"
    const val CONCURRENT = "maxConcurrent"

    /**
     * The manifest's `autonomy` object with [values] applied on top.
     *
     * It takes the **manifest**, not the child object: every caller that had to unwrap `autonomy`
     * itself was one mistake away from sending an empty replacement, which is the bug this exists
     * to prevent.
     *
     * A value of `null`, zero or less **removes** the key: unset is how the manifest spells
     * "the daemon's default", and writing 0 would mean a cap of zero.
     */
    fun merge(manifest: JsonObject?, values: Map<String, Int?>): JsonObject {
        val merged = LinkedHashMap<String, JsonPrimitive>()
        runCatching { manifest?.get("autonomy")?.jsonObject }.getOrNull()?.forEach { (key, value) ->
            (value as? JsonPrimitive)?.let { merged[key] = it }
        }
        values.forEach { (key, value) ->
            if (value == null || value <= 0) merged.remove(key) else merged[key] = JsonPrimitive(value)
        }
        return JsonObject(merged)
    }

    /** One limit as the manifest holds it, or null when it falls back to the daemon's default. */
    fun value(manifest: JsonObject?, key: String): Int? =
        runCatching {
            manifest?.get("autonomy")?.jsonObject?.get(key)?.jsonPrimitive?.intOrNull
        }.getOrNull()
}
