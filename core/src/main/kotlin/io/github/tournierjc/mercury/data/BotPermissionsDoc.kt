package io.github.tournierjc.mercury.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

/**
 * The fine permissions a bot's `permissions.yaml` holds, and the rule that keeps an edit from
 * erasing its neighbours.
 *
 * `PUT /api/bots/:id/permissions` replaces the whole document. Writing `{paths:[…]}` therefore
 * drops `tier`, `blockedCommands`, `tools` — and anything a newer daemon added that this build has
 * never heard of. Every edit here starts from the document the screen already holds and touches one
 * key, so the merge rule is plain data and can be tested without a daemon.
 */
object BotPermissionsDoc {

    const val PATHS = "paths"
    const val BLOCKED_COMMANDS = "blockedCommands"
    const val AUTO_APPROVE_COMMANDS = "autoApproveCommands"
    const val TOOLS = "tools"
    const val ALLOW = "allow"
    const val DENY = "deny"

    const val SCOPE = "scope"
    const val READ = "read"
    const val WRITE = "write"
    const val EXECUTE = "execute"

    /** One `paths` entry: what the bot may do under [scope]. `*` stands for the whole workspace. */
    data class PathGrant(
        val scope: String,
        val read: Boolean = false,
        val write: Boolean = false,
        val execute: Boolean = false,
    )

    /** The `paths` the document grants, in the order it lists them. Entries without a scope are not grants. */
    fun paths(doc: JsonObject?): List<PathGrant> {
        val array = doc?.get(PATHS) as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val entry = element as? JsonObject ?: return@mapNotNull null
            val scope = entry.string(SCOPE)?.trim().orEmpty()
            if (scope.isEmpty()) return@mapNotNull null
            PathGrant(scope, entry.flag(READ), entry.flag(WRITE), entry.flag(EXECUTE))
        }
    }

    /** A top-level string list (`blockedCommands`, `autoApproveCommands`), empty when absent. */
    fun commands(doc: JsonObject?, key: String): List<String> = strings(doc?.get(key) as? JsonArray)

    /** `tools.allow` or `tools.deny`, empty when absent. */
    fun tools(doc: JsonObject?, key: String): List<String> =
        strings((doc?.get(TOOLS) as? JsonObject)?.get(key) as? JsonArray)

    /**
     * Whether [draft] holds something [stored] does not.
     *
     * The save button and the "you are about to lose this" prompt both hang off this. Keying them on
     * "a draft exists" was wrong: flipping a switch on and back off left the button lit and the
     * prompt armed for a change that no longer exists. A draft equal to the stored document is not an
     * edit — including the case where neither exists.
     */
    fun isDirty(stored: JsonObject?, draft: JsonObject?): Boolean = draft != null && draft != stored

    /**
     * The document with [grant] written under `paths`, at the position the scope already had.
     *
     * A blank scope is not a path — the field is empty while it is being typed, and writing `""`
     * would grant nothing while looking like a row. Re-adding an existing scope **replaces** its
     * flags instead of appending a second entry: the daemon reads the first match, so a duplicate
     * would make the visible switch and the applied rule disagree.
     */
    fun withPath(doc: JsonObject?, grant: PathGrant): JsonObject {
        val scope = grant.scope.trim()
        if (scope.isEmpty()) return doc ?: JsonObject(emptyMap())
        val entries = pathEntries(doc)
        entries[scope] = buildJsonObject {
            put(SCOPE, JsonPrimitive(scope))
            put(READ, JsonPrimitive(grant.read))
            put(WRITE, JsonPrimitive(grant.write))
            put(EXECUTE, JsonPrimitive(grant.execute))
        }
        return (doc ?: JsonObject(emptyMap())).replacing(PATHS, JsonArray(entries.values.toList()))
    }

    /**
     * The document without [scope]. An emptied `paths` stays as `[]` rather than disappearing: it is
     * the document's backbone, and "no path" is a state the owner asked for, not a missing field.
     */
    fun withoutPath(doc: JsonObject?, scope: String): JsonObject {
        val target = scope.trim()
        if (target.isEmpty()) return doc ?: JsonObject(emptyMap())
        val entries = pathEntries(doc)
        entries.remove(target)
        return (doc ?: JsonObject(emptyMap())).replacing(PATHS, JsonArray(entries.values.toList()))
    }

    /**
     * The document with the top-level [key] list replaced. An emptied list **removes** the key: the
     * daemon reads a missing list as "no rule", and an empty array it has to keep is a file growing
     * keys nobody reads. Values are trimmed, deduplicated, and blanks dropped — `a, a,` is one rule.
     */
    fun withCommands(doc: JsonObject?, key: String, values: List<String>): JsonObject =
        (doc ?: JsonObject(emptyMap())).replacing(key, listOrNull(values))

    /**
     * The document with `tools.[key]` replaced, keeping the sibling list and any other key the
     * daemon keeps in `tools`.
     *
     * `tools` disappears when both lists are gone: `{}` and an absent object mean the same thing to
     * the daemon, and an empty object invites the next reader to think a gate exists.
     */
    fun withTools(doc: JsonObject?, key: String, values: List<String>): JsonObject {
        val base = doc ?: JsonObject(emptyMap())
        val tools = LinkedHashMap<String, JsonElement>()
        (base[TOOLS] as? JsonObject)?.forEach { (name, value) -> tools[name] = value }
        val list = listOrNull(values)
        if (list == null) tools.remove(key) else tools[key] = list
        return base.replacing(TOOLS, if (tools.isEmpty()) null else JsonObject(tools))
    }

    /**
     * The document with the entry for [from] renamed to [to], keeping its flags and its position.
     *
     * Renaming is not remove-then-add: the row would jump to the bottom of the list under the
     * finger that just fixed a typo, and the owner would have to find it again to flip a switch.
     * Renaming onto a folder that already exists folds the two entries, because two `paths` rows
     * for one folder disagree about what the bot may do.
     */
    fun renamePath(doc: JsonObject?, from: String, to: String): JsonObject {
        val source = from.trim()
        val target = to.trim()
        if (source.isEmpty() || target.isEmpty() || source == target) return doc ?: JsonObject(emptyMap())
        val existing = pathEntries(doc)
        // The folder that already existed wins: renaming onto it must not hand its rows the flags
        // of the one being renamed, which would silently widen or narrow a grant the owner did not
        // touch. Folding one entry into the other means dropping the source, not overwriting.
        val collides = existing.containsKey(target)
        val entries = LinkedHashMap<String, JsonObject>()
        existing.forEach { (scope, entry) ->
            when {
                scope == source && !collides -> entries[target] = entry.renamed(target)
                scope == source -> Unit
                else -> entries[scope] = entry
            }
        }
        return (doc ?: JsonObject(emptyMap())).replacing(PATHS, JsonArray(entries.values.toList()))
    }

    /**
     * What a field holding `a, b` or one entry per line means.
     *
     * Both separators: a command with arguments is long enough that one per line is the only
     * readable way to type it on a phone, and pasting a comma-separated list should not hand the
     * runner one entry called `a, b`.
     */
    fun splitList(text: String): List<String> =
        text.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /** The `paths` array as scope → entry, dropping entries this app cannot address. */
    private fun pathEntries(doc: JsonObject?): LinkedHashMap<String, JsonObject> {
        val entries = LinkedHashMap<String, JsonObject>()
        (doc?.get(PATHS) as? JsonArray)?.forEach { element ->
            val entry = element as? JsonObject ?: return@forEach
            val scope = entry.string(SCOPE)?.trim().orEmpty()
            if (scope.isEmpty()) return@forEach
            entries[scope] = entry
        }
        return entries
    }

    private fun listOrNull(values: List<String>): JsonArray? {
        val cleaned = values.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return if (cleaned.isEmpty()) null else JsonArray(cleaned.map { JsonPrimitive(it) })
    }

    private fun strings(array: JsonArray?): List<String> =
        array?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { text -> text.isNotEmpty() } }
            .orEmpty()

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.flag(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false

    /** The same entry under a new folder, keeping every other key it carried. */
    private fun JsonObject.renamed(scope: String): JsonObject {
        val copy = LinkedHashMap<String, JsonElement>(this)
        copy[SCOPE] = JsonPrimitive(scope)
        return JsonObject(copy)
    }

    private fun JsonObject.replacing(key: String, value: JsonElement?): JsonObject {
        val copy = LinkedHashMap<String, JsonElement>(this)
        if (value == null) copy.remove(key) else copy[key] = value
        return JsonObject(copy)
    }
}
