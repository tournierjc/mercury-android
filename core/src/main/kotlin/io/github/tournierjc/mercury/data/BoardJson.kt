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
    private val IMAGE_SUFFIXES = listOf(".png", ".jpg", ".jpeg", ".webp", ".gif", ".svg")
    private val TEXT_SUFFIXES = listOf(".md", ".markdown", ".txt", ".json", ".csv", ".log", ".yml", ".yaml")

    fun id(node: JsonObject?): String = firstString(node, ID_KEYS).orEmpty()

    fun title(node: JsonObject?): String =
        firstString(node, TITLE_KEYS).orEmpty().ifBlank { id(node) }

    /**
     * The six statuses both clients share, in the order every surface renders them: what waits for
     * the owner first, then what runs, then what ended (SPECS/111 §1).
     */
    val STATUS_ORDER = listOf("pending", "paused", "question", "running", "done", "failed")

    fun status(node: JsonObject?): String =
        firstString(node, STATUS_KEYS).orEmpty().ifBlank { STATUS_ORDER.first() }

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
     * A card attachment is `{id,name,path,type,size,addedBy,addedAt}`. `path` is the
     * server's own file, meaningless on a phone: reading it goes through the two file
     * routes, never through the stored path.
     */
    fun attachmentType(node: JsonObject?): String = firstString(node, listOf("type", "kind")).orEmpty()

    fun attachmentSize(node: JsonObject?): Long? = longField(node, "size")

    /** The daemon types attachments `markdown|document|image|presentation|other`. */
    fun attachmentIsImage(node: JsonObject?): Boolean {
        if (attachmentType(node) == "image") return true
        val name = title(node).lowercase()
        return IMAGE_SUFFIXES.any { name.endsWith(it) }
    }

    /**
     * `/content` reads the file as UTF-8, so it only says something for a text deliverable.
     * A `.pptx` or a `.pdf` comes back as noise: those go straight to the viewer.
     */
    fun attachmentIsText(node: JsonObject?): Boolean {
        if (attachmentIsImage(node)) return false
        val name = title(node).lowercase()
        return attachmentType(node) == "markdown" || TEXT_SUFFIXES.any { name.endsWith(it) }
    }

    /**
     * The type the `/download` route announces, as a fallback for the one it sends in
     * `Content-Type` — same table as the handler, so a viewer gets the same type either way.
     */
    fun attachmentMimeType(node: JsonObject?): String {
        val name = title(node).lowercase()
        return when {
            IMAGE_SUFFIXES.any { name.endsWith(it) } || attachmentType(node) == "image" -> when {
                name.endsWith(".png") -> "image/png"
                name.endsWith(".svg") -> "image/svg+xml"
                name.endsWith(".webp") -> "image/webp"
                name.endsWith(".gif") -> "image/gif"
                else -> "image/jpeg"
            }
            attachmentType(node) == "markdown" || name.endsWith(".md") -> "text/markdown"
            attachmentType(node) == "presentation" ->
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"

            name.endsWith(".pdf") -> "application/pdf"
            name.endsWith(".json") -> "application/json"
            name.endsWith(".txt") -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    /** `GET …/attachments/:id/content` answers `{ok,attachment,content,size}`, file read as UTF-8. */
    fun attachmentContent(root: JsonObject?): String =
        (root?.get("content") as? JsonPrimitive)?.contentOrNull.orEmpty()

    /** The question a `feedback-request` frame asks the owner. */
    fun feedbackQuestion(node: JsonObject?): String = firstString(node, listOf("question", "prompt", "message")).orEmpty()

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

    /**
     * Groups cards by status in [STATUS_ORDER]. An absent status is not rendered; an unknown one
     * keeps its raw key and comes after the six, in the order Mercury sent it — folding it into
     * `pending` would hide the one fact worth seeing, the name the server used (SPECS/111 §1).
     * `groupBy` keeps the encounter order inside a group, so cards stay in server order.
     */
    fun byStatus(cards: List<JsonObject>): List<Pair<String, List<JsonObject>>> {
        val groups = cards.groupBy { status(it) }
        val known = STATUS_ORDER.mapNotNull { key -> groups[key]?.let { key to it } }
        val unknown = groups.filterKeys { it !in STATUS_ORDER }.toList()
        return known + unknown
    }

    /**
     * `available:false` means the boards service did not start on this instance — not that the
     * owner has no boards. The two states read the same on screen and must not (SPECS/110 §5).
     */
    fun available(root: JsonObject?): Boolean? = boolField(root, "available")

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
