package io.github.tournierjc.mercury.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Boards are Mercury's kanban execution surface and, on the reference instance,
 * never created — so their wire shape has never been observed. Instead of
 * inventing a schema that would break on the first rename, the client navigates
 * the JSON defensively. These helpers are the single place that knows the key
 * names, and they are unit tested against the shapes the handlers emit.
 */
object BoardJson {

    private val ID_KEYS = listOf("id", "cardId", "boardId")
    private val TITLE_KEYS = listOf("task", "title", "name", "label", "summary")
    private val STATUS_KEYS = listOf("status", "state", "column", "columnId", "lane")

    fun id(node: JsonObject?): String = firstString(node, ID_KEYS).orEmpty()

    fun title(node: JsonObject?): String =
        firstString(node, TITLE_KEYS).orEmpty().ifBlank { id(node) }

    fun status(node: JsonObject?): String = firstString(node, STATUS_KEYS).orEmpty().ifBlank { "todo" }

    fun description(node: JsonObject?): String = firstString(node, listOf("description", "detail", "notes")).orEmpty()

    fun priority(node: JsonObject?): String = firstString(node, listOf("priority")).orEmpty()

    fun intField(node: JsonObject?, vararg keys: String): Int? {
        if (node == null) return null
        for (key in keys) {
            val value = node[key] as? JsonPrimitive ?: continue
            value.intOrNull?.let { return it }
        }
        return null
    }

    fun boolField(node: JsonObject?, vararg keys: String): Boolean? {
        if (node == null) return null
        for (key in keys) {
            val value = node[key] as? JsonPrimitive ?: continue
            value.booleanOrNull?.let { return it }
        }
        return null
    }

    fun longField(node: JsonObject?, vararg keys: String): Long? {
        if (node == null) return null
        for (key in keys) {
            val value = node[key] as? JsonPrimitive ?: continue
            value.longOrNull?.let { return it }
        }
        return null
    }

    /** `GET /api/boards` answers `{boards:[…]}`; a detail answer may nest the cards. */
    fun boards(root: JsonObject?): List<JsonObject> = array(root, "boards")

    fun cards(root: JsonObject?): List<JsonObject> =
        array(root, "cards").ifEmpty { array(root?.get("board") as? JsonObject, "cards") }

    fun comments(root: JsonObject?): List<JsonObject> = array(root, "comments")
    fun labels(node: JsonObject?): List<JsonObject> = array(node, "labels")
    fun attachments(root: JsonObject?): List<JsonObject> = array(root, "attachments")
    fun dependencies(root: JsonObject?): List<JsonObject> =
        array(root, "dependencies").ifEmpty { array(root, "dependsOn") }
    /**
     * `GET /api/boards/:id/execution-plan` answers `{ok, batches}` where a batch is
     * either a list of card ids or a list of card objects — both are flattened to
     * displayable labels.
     */
    fun batchLabels(root: JsonObject?): List<List<String>> {
        val array = root?.get("batches") as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return array.mapNotNull { batch ->
            runCatching {
                batch.jsonArray.map { entry ->
                    runCatching { entry.jsonObject }.getOrNull()?.let { title(it) }
                        ?: (entry as? JsonPrimitive)?.contentOrNull.orEmpty()
                }
            }.getOrNull()
        }
    }
    fun feedback(root: JsonObject?): List<JsonObject> = array(root, "feedback")

    /** Groups cards by status, keeping the order Mercury returned. */
    fun byStatus(cards: List<JsonObject>): List<Pair<String, List<JsonObject>>> =
        cards.groupBy { status(it) }.toList()

    private fun array(node: JsonObject?, key: String): List<JsonObject> {
        val element = node?.get(key) as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return element.mapNotNull { entry ->
            runCatching { entry.jsonObject }.getOrNull()
                // Some routes answer with bare ids (`dependsOn:["c9"]`); normalise them.
                ?: (entry as? JsonPrimitive)?.contentOrNull?.let { id -> JsonObject(mapOf("id" to JsonPrimitive(id))) }
        }
    }

    private fun firstString(node: JsonObject?, keys: List<String>): String? {
        if (node == null) return null
        for (key in keys) {
            val value = (node[key] as? JsonPrimitive)?.contentOrNull
            if (!value.isNullOrBlank()) return value
        }
        return null
    }
}
