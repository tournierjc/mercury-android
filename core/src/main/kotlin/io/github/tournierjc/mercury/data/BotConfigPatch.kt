package io.github.tournierjc.mercury.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * The three manifest objects this app writes whole: `memory`, `comms` and `retention`.
 *
 * `PATCH /api/bots/:id` replaces every top-level object it carries, so sending a partial
 * `memory` erases the keys it omits — the same trap [BotAutonomy] documents for the limits.
 * Every builder here starts from the manifest the sheet already holds and applies a delta on
 * top. A delta entry of `null` — or a blank string, an empty list, a non-positive number —
 * **removes** the key: absence is how the manifest spells "the daemon's default", and writing
 * zero would mean a cap of zero.
 */
object BotConfigPatch {

    const val MEMORY = "memory"
    const val COMMS = "comms"
    const val RETENTION = "retention"

    // `memory`
    const val SCOPE = "scope"
    const val LEARN = "learn"
    const val ALLOW_CROSS_BOT_RECALL = "allowCrossBotRecall"

    /** The three scopes the daemon's type allows, spelled as it spells them. */
    const val SCOPE_NONE = "none"
    const val SCOPE_OWN = "own"
    const val SCOPE_SHARED_READ = "shared-read"

    // `comms` — the list the daemon injects into the bot's prompt as its roster
    const val CAN_MESSAGE = "canMessage"

    // `retention`
    const val TRANSCRIPT_RUNS = "transcriptRuns"
    const val JOURNAL_ROTATE_BYTES = "journalRotateBytes"
    const val JOURNAL_KEEP_ROTATIONS = "journalKeepRotations"
    const val MAILBOX_TTL_HOURS = "mailboxTtlHours"
    const val DLQ_CAP = "dlqCap"
    const val ARTIFACT_QUOTA_BYTES = "artifactQuotaBytes"
    const val SANDBOX_JANITOR = "sandboxJanitor"
    const val JANITOR_ENABLED = "enabled"
    const val HOT_DAYS = "hotDays"
    const val ARCHIVE_DAYS = "archiveDays"

    /** The manifest's object under [key], verbatim, or an empty one when it holds none. */
    fun current(manifest: JsonObject?, key: String): JsonObject =
        runCatching { manifest?.get(key)?.jsonObject }.getOrNull() ?: JsonObject(emptyMap())

    /**
     * [key]'s object with [values] applied on top of it.
     *
     * A key absent from [values] keeps the manifest's value; a key present with `null` is
     * removed. Unknown keys the manifest carries are copied through, so an object the daemon
     * grows keeps its newer fields when the phone edits an older one.
     */
    fun merge(manifest: JsonObject?, key: String, values: Map<String, JsonElement?>): JsonObject =
        mergeInto(current(manifest, key), values)

    /**
     * The complete `memory` object.
     *
     * Callers pass what they read back from the manifest: a field they do not name is removed,
     * which for a bot that never had it is the state it was already in.
     */
    fun memory(
        manifest: JsonObject?,
        scope: String? = null,
        learn: Boolean? = null,
        recall: List<String>? = null,
    ): JsonObject = merge(
        manifest,
        MEMORY,
        mapOf(
            SCOPE to scope?.takeIf { it.isNotBlank() }?.let(::JsonPrimitive),
            LEARN to learn?.let(::JsonPrimitive),
            ALLOW_CROSS_BOT_RECALL to recall?.takeIf { it.isNotEmpty() }?.let(::primitives),
        ),
    )

    /** The complete `comms` object: the roster the bot is told it may write to. */
    fun comms(manifest: JsonObject?, canMessage: List<String>?): JsonObject = merge(
        manifest,
        COMMS,
        mapOf(CAN_MESSAGE to canMessage?.takeIf { it.isNotEmpty() }?.let(::primitives)),
    )

    /**
     * The complete `retention` object.
     *
     * [numbers] is a delta: a key it does not carry keeps the manifest's value, a `null` or
     * non-positive one removes it. [janitor] replaces the `sandboxJanitor` sub-object the same
     * way, and an empty result removes the sub-object rather than sending `{}` — a hollow object
     * would read as "configured" to anything that only looks for its presence.
     */
    fun retention(
        manifest: JsonObject?,
        numbers: Map<String, Long?> = emptyMap(),
        janitor: Janitor? = null,
    ): JsonObject {
        val values = LinkedHashMap<String, JsonElement?>(numbers.size + 1)
        numbers.forEach { (key, value) -> values[key] = value?.takeIf { it > 0 }?.let(::JsonPrimitive) }
        if (janitor != null) {
            val merged = mergeInto(node(manifest, RETENTION, SANDBOX_JANITOR) ?: JsonObject(emptyMap()), janitor.delta())
            values[SANDBOX_JANITOR] = merged.takeIf { it.isNotEmpty() }
        }
        return merge(manifest, RETENTION, values)
    }

    /**
     * The sandbox janitor's three fields.
     *
     * `null` removes a key, so a caller passes all three as it read them — the retention section
     * does, and a half-filled delta would drop the durations it did not name.
     */
    data class Janitor(val enabled: Boolean? = null, val hotDays: Long? = null, val archiveDays: Long? = null) {
        fun delta(): Map<String, JsonElement?> = mapOf(
            JANITOR_ENABLED to enabled?.let(::JsonPrimitive),
            HOT_DAYS to hotDays?.takeIf { it > 0 }?.let(::JsonPrimitive),
            ARCHIVE_DAYS to archiveDays?.takeIf { it > 0 }?.let(::JsonPrimitive),
        )
    }

    // ------------------------------------------------------------------ readers
    // The sheet shows what the manifest holds; these keep the JSON casting out of the composables.

    fun text(manifest: JsonObject?, key: String, name: String): String? =
        primitive(manifest, key, name)?.takeIf { it.isString }?.content

    fun number(manifest: JsonObject?, key: String, name: String): Long? =
        primitive(manifest, key, name)?.longOrNull

    fun flag(manifest: JsonObject?, key: String, name: String): Boolean? =
        primitive(manifest, key, name)?.booleanOrNull

    fun strings(manifest: JsonObject?, key: String, name: String): List<String> =
        (current(manifest, key)[name] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .orEmpty()

    fun node(manifest: JsonObject?, key: String, name: String): JsonObject? =
        runCatching { current(manifest, key)[name]?.jsonObject }.getOrNull()

    /** `sandboxJanitor.enabled`, or null when the sub-object or the key is absent. */
    fun janitorEnabled(manifest: JsonObject?): Boolean? =
        (node(manifest, RETENTION, SANDBOX_JANITOR)?.get(JANITOR_ENABLED) as? JsonPrimitive)?.booleanOrNull

    /** One of the janitor's two durations, in days. */
    fun janitorDays(manifest: JsonObject?, name: String): Long? =
        (node(manifest, RETENTION, SANDBOX_JANITOR)?.get(name) as? JsonPrimitive)?.longOrNull

    private fun mergeInto(current: JsonObject, values: Map<String, JsonElement?>): JsonObject {
        val merged = LinkedHashMap<String, JsonElement>(current)
        // `JsonNull` reads as "remove this key" too: the wire never carries the null, so a caller
        // can say "cleared" with a typed delta instead of a Kotlin nullable map value.
        values.forEach { (key, value) ->
            if (value == null || value is JsonNull) merged.remove(key) else merged[key] = value
        }
        return JsonObject(merged)
    }

    private fun primitives(values: List<String>): JsonArray = JsonArray(values.map(::JsonPrimitive))

    private fun primitive(manifest: JsonObject?, key: String, name: String): JsonPrimitive? =
        current(manifest, key)[name] as? JsonPrimitive
}
