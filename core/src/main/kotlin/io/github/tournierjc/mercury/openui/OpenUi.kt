package io.github.tournierjc.mercury.openui

import io.github.tournierjc.mercury.data.Notice
import io.github.tournierjc.mercury.data.NoticeKey

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * OpenUI Lang v1, as the `openui-contract` plugin defines it.
 *
 * The client's job is narrow on purpose: parse what the contract allows and say
 * "no" to everything else, so [io.github.tournierjc.mercury.ui.OpenUiBlock] can render
 * a spec without ever wondering whether a component is safe. Anything this refuses
 * falls back to the fenced code block — a message is never lost, it is just not drawn.
 *
 * Rules mirrored from `mercury-plugin-openui/CONTRACT.md`: `id = Component(args)` one
 * per line, ids `[a-z][a-z0-9_]{0,23}`, layout components take bare ids, props are one
 * JSON object, nine components, nothing pointing at a URL or a script.
 */
object OpenUi {

    const val FENCE = "openui"

    private const val MAX_NODES = 60
    private const val MAX_STRING = 1200
    private const val MAX_CODE = 4000
    private const val MAX_ITEMS = 60

    private val ID = Regex("^[a-z][a-z0-9_]{0,23}$")
    private val STATEMENT = Regex("^([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*([A-Z][A-Za-z0-9]*)\\s*\\((.*)\\)$")

    private val LAYOUT = setOf("Stack")

    private val COMPONENTS = setOf(
        "Stack", "Text", "Card", "KeyValue", "Table", "List", "Stat", "Code", "Chart", "Pie", "Gauge",
    )

    /** Props that would make a client fetch, execute or embed something. Refused whole-spec. */
    private val FORBIDDEN = setOf(
        "href", "url", "uri", "src", "srcset", "html", "innerhtml", "script", "style",
        "onclick", "onload", "onerror", "action", "formaction", "image", "img", "svg",
        "iframe", "embed", "object", "data", "background", "class", "classname",
    )

    private val ALLOWED_PROPS = mapOf(
        "Text" to setOf("text", "variant", "muted"),
        "Card" to setOf("title", "body", "tone"),
        "KeyValue" to setOf("label", "value", "highlight"),
        "Table" to setOf("columns", "rows", "caption"),
        "List" to setOf("items", "ordered"),
        "Stat" to setOf("label", "value", "delta", "tone"),
        "Code" to setOf("code", "language", "title"),
        "Chart" to setOf("kind", "labels", "values", "title", "unit"),
        "Pie" to setOf("kind", "labels", "values", "title", "unit", "legend"),
        "Gauge" to setOf("value", "max", "label", "unit", "tone", "title"),
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    /**
     * Root of a parsed spec, or why it cannot be rendered yet.
     *
     * [Pending] only ever comes out of a streaming parse: the fence is still being written,
     * nothing complete enough to draw has arrived, and there is no error to report.
     */
    sealed interface Result {
        data class Rendered(val root: OpenUiNode) : Result
        data class Refused(val errors: List<Notice>) : Result
        data object Pending : Result
    }

    /**
     * Parse a spec.
     *
     * [partial] is the streaming mode: the block is still being written, so the last line of
     * the text is the line being typed — dropping it is "wait for the next chunk", not an
     * error — and structural completeness is not expected yet, so children that have not
     * arrived are skipped instead of failing the spec. A closed spec keeps the strict
     * contract: nothing missing, nothing dangling, no cycle.
     *
     * The rules are the ones `openui-contract`'s parser applies while streaming, so a spec
     * drawn by the dashboard and by the phone is drawn at the same moment.
     */
    /** One diagnostic on line `line`: the number leads, the rest are arguments. */
    private fun at(line: Int, key: NoticeKey, vararg args: String): Notice =
        Notice(key, args = listOf(line.toString()) + args)

    fun parse(spec: String, partial: Boolean = false): Result {
        val errors = mutableListOf<Notice>()
        if (spec.isBlank()) {
            return if (partial) Result.Pending else Result.Refused(listOf(Notice(NoticeKey.OPENUI_EMPTY)))
        }
        if (spec.length > 32768) return Result.Refused(listOf(Notice(NoticeKey.OPENUI_TOO_LONG)))

        val nodes = LinkedHashMap<String, Node>()
        val lines = spec.lines()
        lines.forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("#")) return@forEachIndexed
            val number = index + 1
            // The last line is the one being typed: drop it while streaming, report it once closed.
            val typed = partial && index == lines.lastIndex
            val match = STATEMENT.find(line)
            if (match == null) {
                if (!typed) errors += at(number, NoticeKey.OPENUI_LINE_EXPECT)
                return@forEachIndexed
            }
            val (id, component, rawArgs) = match.destructured
            if (!ID.matches(id)) {
                errors += at(number, NoticeKey.OPENUI_LINE_BAD_ID, id)
                return@forEachIndexed
            }
            if (nodes.containsKey(id)) {
                errors += at(number, NoticeKey.OPENUI_DUPLICATE_ID, id)
                return@forEachIndexed
            }
            if (component !in COMPONENTS) {
                errors += at(number, NoticeKey.OPENUI_LINE_UNKNOWN_COMPONENT, component)
                return@forEachIndexed
            }
            if (component in LAYOUT) {
                val children = parseIds(rawArgs)
                if (children.isEmpty()) {
                    if (!typed) errors += at(number, NoticeKey.OPENUI_LINE_ID_LIST, component)
                } else {
                    nodes[id] = Node(id, component, children = children)
                }
                return@forEachIndexed
            }
            val props = runCatching { json.parseToJsonElement(rawArgs.trim()) as? JsonObject }.getOrNull()
            if (props == null) {
                if (!typed) errors += at(number, NoticeKey.OPENUI_LINE_PROPS_OBJECT)
                return@forEachIndexed
            }
            errors += checkProps(id, component, props, number)
            errors += checkSemantics(id, component, props, number)
            nodes[id] = Node(id, component, props = props)
        }

        if (nodes.size > MAX_NODES) errors += Notice(NoticeKey.OPENUI_TOO_MANY_COMPONENTS, args = listOf(nodes.size.toString(), MAX_NODES.toString()))
        // A closed spec must be whole. While streaming these checks are premature: the tree
        // is built from what has arrived and drawn as soon as anything is drawable.
        if (errors.isEmpty() && !partial) {
            if (nodes["root"] == null) errors += Notice(NoticeKey.OPENUI_NO_ROOT)
            for (node in nodes.values) {
                for (child in node.children) {
                    if (child !in nodes) errors += Notice(NoticeKey.OPENUI_DANGLING_CHILD, args = listOf(node.id, child))
                }
            }
            val referenced = nodes.values.flatMap { it.children }.toSet()
            nodes.values.firstOrNull { it.id != "root" && it.id !in referenced }?.let {
                errors += Notice(NoticeKey.OPENUI_UNREFERENCED, args = listOf(it.id))
            }
            if (errors.isEmpty() && hasCycle(nodes, "root", mutableSetOf(), mutableSetOf())) {
                errors += Notice(NoticeKey.OPENUI_CYCLE)
            }
        }
        if (errors.isNotEmpty()) return Result.Refused(errors)

        val built = build(nodes, "root", mutableSetOf())
            ?: return if (partial) Result.Pending else Result.Refused(listOf(Notice(NoticeKey.OPENUI_TREE)))
        return Result.Rendered(built)
    }

    private fun checkProps(id: String, component: String, props: JsonObject, line: Int): List<Notice> {
        val allowed = ALLOWED_PROPS[component].orEmpty()
        val errors = mutableListOf<Notice>()
        for ((key, value) in props) {
            if (key.lowercase() in FORBIDDEN) {
                errors += at(line, NoticeKey.OPENUI_LINE_FORBIDDEN_PROP, key)
                continue
            }
            if (key !in allowed) {
                errors += at(line, NoticeKey.OPENUI_LINE_NOT_A_PROP, key, component)
                continue
            }
            when {
                key == "rows" -> if (!isStringMatrix(value)) errors += at(line, NoticeKey.OPENUI_ROWS)
                key == "columns" || key == "labels" || key == "items" ->
                    if (!isStringList(value, MAX_STRING)) errors += at(line, NoticeKey.OPENUI_STRING_LIST, key)
                key == "values" -> if (!isNumberList(value)) errors += at(line, NoticeKey.OPENUI_NUMBER_LIST)
                // `isString` matters: JsonPrimitive.doubleOrNull happily parses "42", and a
                // quoted number is exactly what the contract refuses — a renderer that has to
                // parse a string is a renderer that guesses.
                key == "value" && component == "Gauge" -> {
                    val number = (value as? JsonPrimitive)
                    if (number == null || number.isString || number.doubleOrNull == null) {
                        errors += at(line, NoticeKey.OPENUI_NUMBER)
                    }
                }
                key == "max" -> {
                    val number = value as? JsonPrimitive
                    if (number == null || number.isString || number.doubleOrNull == null || number.doubleOrNull!! <= 0) {
                        errors += at(line, NoticeKey.OPENUI_POSITIVE)
                    }
                }
                key == "legend" -> if (value.jsonPrimitive.booleanOrNull == null) {
                    errors += at(line, NoticeKey.OPENUI_BOOLEAN, "legend")
                }
                key == "highlight" || key == "ordered" || key == "muted" ->
                    if (value.jsonPrimitive.booleanOrNull == null) errors += at(line, NoticeKey.OPENUI_BOOLEAN, key)
                key == "code" -> {
                    val text = (value as? JsonPrimitive)?.contentOrNullSafe()
                    if (text == null || text.length > MAX_CODE) errors += at(line, NoticeKey.OPENUI_CODE_LONG, MAX_CODE.toString())
                }
                key == "kind" -> {
                    // `kind` names a variant of the component, not a fixed vocabulary: a Chart
                    // is bar or line, a Pie is pie or donut.
                    val allowedKinds = if (component == "Pie") setOf("pie", "donut") else setOf("bar", "line")
                    if ((value as? JsonPrimitive)?.contentOrNullSafe() !in allowedKinds) {
                        errors += at(line, NoticeKey.OPENUI_LINE_BAD_KIND, allowedKinds.joinToString(" / "))
                    }
                }
                key == "tone" -> if ((value as? JsonPrimitive)?.contentOrNullSafe() !in setOf("neutral", "ok", "warn", "danger")) {
                    errors += at(line, NoticeKey.OPENUI_LINE_UNKNOWN_TONE)
                }
                key == "variant" -> if ((value as? JsonPrimitive)?.contentOrNullSafe() !in setOf("body", "h1", "h2", "h3", "mono")) {
                    errors += at(line, NoticeKey.OPENUI_LINE_UNKNOWN_VARIANT)
                }
                else -> {
                    val text = (value as? JsonPrimitive)?.contentOrNullSafe()
                    if (text == null) errors += at(line, NoticeKey.OPENUI_STRING, key)
                    else if (text.length > MAX_STRING) errors += at(line, NoticeKey.OPENUI_STRING_LONG, key, MAX_STRING.toString())
                }
            }
        }
        return errors
    }

    /**
     * Rules a per-prop type cannot express, mirrored from the daemon's parser: a pie needs as
     * many labels as values, no negative slice and something to divide.
     */
    private fun checkSemantics(id: String, component: String, props: JsonObject, line: Int): List<Notice> {
        if (component != "Pie") return emptyList()
        val errors = mutableListOf<Notice>()
        val labels = (props["labels"] as? JsonArray).orEmpty()
        val values = (props["values"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull }.orEmpty()
        if (labels.size != values.size) {
            errors += at(line, NoticeKey.OPENUI_LABELS_VALUES, id, labels.size.toString(), values.size.toString())
        }
        if (labels.size > 12) errors += at(line, NoticeKey.OPENUI_LINE_TOO_MANY_SHARES, id, labels.size.toString())
        if (values.any { it < 0 }) errors += at(line, NoticeKey.OPENUI_NEGATIVE_SHARE, id)
        if (values.isNotEmpty() && values.sum() <= 0) errors += at(line, NoticeKey.OPENUI_ZERO_SHARES, id)
        return errors
    }

    /**
     * A bare fence whose body is a complete spec.
     *
     * Models forget the language tag — ` ``` ` instead of ` ```openui ` — and the answer then
     * arrived as source code the owner had to read. The tag is claimed back only when the
     * whole body parses: a snippet that merely mentions a component stays a code block, which
     * is the conservative half of a guess.
     */
    fun looksLikeSpec(text: String): Boolean {
        val mentionsComponent = text.lineSequence().any { line ->
            COMPONENTS.any { component -> line.contains("= $component(") }
        }
        if (!mentionsComponent) return false
        return parse(text) is Result.Rendered
    }

    private fun parseIds(raw: String): List<String> {
        val inner = raw.trim().removePrefix("[").removeSuffix("]")
        if (inner.isBlank()) return emptyList()
        return inner.split(',').map { it.trim() }.filter { ID.matches(it) }
    }

    private fun isStringList(value: kotlinx.serialization.json.JsonElement, max: Int): Boolean {
        val array = value as? JsonArray ?: return false
        if (array.size > MAX_ITEMS) return false
        return array.all { (it as? JsonPrimitive)?.contentOrNullSafe()?.let { text -> text.length <= max } == true }
    }

    private fun isStringMatrix(value: kotlinx.serialization.json.JsonElement): Boolean {
        val array = value as? JsonArray ?: return false
        if (array.size > MAX_ITEMS) return false
        return array.all { row -> isStringList(row, MAX_STRING) }
    }

    private fun isNumberList(value: kotlinx.serialization.json.JsonElement): Boolean {
        val array = value as? JsonArray ?: return false
        if (array.size > MAX_ITEMS) return false
        // `doubleOrNull` parses the content whatever the JSON type is, so "12" would pass
        // as a number: the renderer draws a chart by multiplying, and a string there is a
        // NaN waiting for a divide.
        return array.all { item -> item is JsonPrimitive && !item.isString && item.doubleOrNull != null }
    }

    private fun JsonPrimitive.contentOrNullSafe(): String? =
        if (this is JsonPrimitive && !isString) null else content

    /** A cycle would make the renderer recurse until the stack gives out. */
    private fun hasCycle(nodes: Map<String, Node>, id: String, path: MutableSet<String>, done: MutableSet<String>): Boolean {
        if (id in path) return true
        if (id in done) return false
        val node = nodes[id] ?: return false
        path += id
        for (child in node.children) if (hasCycle(nodes, child, path, done)) return true
        path -= id
        done += id
        return false
    }

    /** Build the tree from `root`, refusing a cycle rather than recursing forever. */
    private fun build(nodes: Map<String, Node>, id: String, seen: MutableSet<String>): OpenUiNode? {
        if (!seen.add(id)) return null
        val node = nodes[id] ?: return null
        val props = node.props
        fun text(key: String): String? = (props?.get(key) as? JsonPrimitive)?.contentOrNullSafe()
        fun flag(key: String): Boolean = (props?.get(key) as? JsonPrimitive)?.booleanOrNull ?: false
        fun strings(key: String): List<String> =
            (props?.get(key) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNullSafe() }.orEmpty()
        fun numbers(key: String): List<Double> =
            (props?.get(key) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.doubleOrNull }.orEmpty()
        fun matrix(key: String): List<List<String>> =
            (props?.get(key) as? JsonArray)?.map { row ->
                (row as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNullSafe() }.orEmpty()
            }.orEmpty()

        return when (node.component) {
            "Stack" -> OpenUiNode.Stack(node.children.mapNotNull { build(nodes, it, seen) })
            "Text" -> OpenUiNode.Paragraph(text("text").orEmpty(), text("variant") ?: "body", flag("muted"))
            "Card" -> OpenUiNode.Card(text("title"), text("body"), text("tone") ?: "neutral")
            "KeyValue" -> OpenUiNode.KeyValue(text("label").orEmpty(), text("value").orEmpty(), flag("highlight"))
            "Table" -> OpenUiNode.Table(strings("columns"), matrix("rows"), text("caption"))
            "List" -> OpenUiNode.Bullets(strings("items"), flag("ordered"))
            "Stat" -> OpenUiNode.Stat(text("value").orEmpty(), text("label").orEmpty(), text("delta"), text("tone") ?: "neutral")
            "Code" -> OpenUiNode.Snippet(text("code").orEmpty(), text("language") ?: "text", text("title"))
            "Chart" -> OpenUiNode.Chart(
                kind = text("kind") ?: "bar",
                labels = strings("labels"),
                values = numbers("values"),
                title = text("title"),
                unit = text("unit"),
            )
            "Pie" -> OpenUiNode.Pie(
                kind = text("kind") ?: "donut",
                labels = strings("labels"),
                values = numbers("values"),
                title = text("title"),
                unit = text("unit"),
                legend = (props?.get("legend") as? JsonPrimitive)?.booleanOrNull ?: true,
            )
            "Gauge" -> OpenUiNode.Gauge(
                value = (props?.get("value") as? JsonPrimitive)?.doubleOrNull ?: 0.0,
                max = (props?.get("max") as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 0 } ?: 100.0,
                label = text("label"),
                unit = text("unit"),
                tone = text("tone") ?: "neutral",
                title = text("title"),
            )
            else -> null
        }
    }

    private data class Node(
        val id: String,
        val component: String,
        val children: List<String> = emptyList(),
        val props: JsonObject? = null,
    )
}

/** A parsed, safe tree: exactly what the renderer may draw. */
sealed interface OpenUiNode {
    data class Stack(val children: List<OpenUiNode>) : OpenUiNode
    data class Paragraph(val text: String, val variant: String, val muted: Boolean) : OpenUiNode
    data class Card(val title: String?, val body: String?, val tone: String) : OpenUiNode
    data class KeyValue(val label: String, val value: String, val highlight: Boolean) : OpenUiNode
    data class Table(val columns: List<String>, val rows: List<List<String>>, val caption: String?) : OpenUiNode
    data class Bullets(val items: List<String>, val ordered: Boolean) : OpenUiNode
    data class Stat(val value: String, val label: String, val delta: String?, val tone: String) : OpenUiNode
    data class Snippet(val code: String, val language: String, val title: String?) : OpenUiNode
    data class Chart(
        val kind: String,
        val labels: List<String>,
        val values: List<Double>,
        val title: String?,
        val unit: String?,
    ) : OpenUiNode

    /** A part-to-whole figure: `kind` is `pie` or `donut`, the default. */
    data class Pie(
        val kind: String,
        val labels: List<String>,
        val values: List<Double>,
        val title: String?,
        val unit: String?,
        val legend: Boolean,
    ) : OpenUiNode

    /** One value against its maximum, drawn as a half ring. */
    data class Gauge(
        val value: Double,
        val max: Double,
        val label: String?,
        val unit: String?,
        val tone: String,
        val title: String?,
    ) : OpenUiNode
}
