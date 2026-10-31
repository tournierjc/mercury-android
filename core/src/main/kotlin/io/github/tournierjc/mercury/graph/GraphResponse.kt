package io.github.tournierjc.mercury.graph

import io.github.tournierjc.mercury.data.dto.GraphNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * The graph route's answer, before anyone interprets it: the status as sent and the body as
 * written. The transport hands this over and stops there, so the meaning of a `503` is decided once
 * in [parseGraphResponse] instead of in every caller.
 */
data class GraphRaw(val status: Int, val body: String)

/**
 * What `GET /api/brain/graph` said, in the three issues the screens must tell apart.
 *
 * A missing SQLite is an absent dependency, and the daemon says so as an HTTP error (`503`,
 * `src/web/api/brain.ts:269`) carrying a body that names the cause. A DTO that decodes the body on
 * success only would throw on the status and lose that body, so the client reads it whatever the
 * status and lets this file decide — the same three issues as
 * `ui/src/lib/brainGraph.ts` (`readGraphResponse`) on the webapp side.
 *
 * An unreadable body is an [Error], never an empty graph: a brain with no memory and a brain whose
 * database is missing do not look alike, and only one of them is worth retrying.
 */
sealed interface GraphOutcome {

    /** The route drew a graph. [links] only name ends that are nodes of [nodes]. */
    data class Ok(val nodes: List<GraphNode>, val links: List<GraphLink>) : GraphOutcome

    /** The route answered `available: false` — the graph is not exposed, and [motif] says why. */
    data class Unavailable(val motif: String) : GraphOutcome

    /** Everything else, including a body this client cannot read. [reason] is technical. */
    data class Error(val reason: String) : GraphOutcome
}

/** Stable technical motifs, matched by the tests: they are contracts, not prose. */
object GraphMotifs {
    const val UNREADABLE_BODY = "UNREADABLE_BODY"
    const val NODES_NOT_AN_ARRAY = "NODES_NOT_AN_ARRAY"
    const val NO_READABLE_NODE = "NO_READABLE_NODE"
}

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Reads the answer of the graph route, whatever its status — the single entry point of the
 * contract, shared by the app and proven by a JVM test.
 *
 * The checks run in the order the webapp settled on, so both clients take the same decision on the
 * same bytes:
 *
 * 1. `available: false` first, whatever the status — the daemon answers `503` today and a `200`
 *    tomorrow, and both must reach the same screen state;
 * 2. a non-2xx status without that flag is a [GraphOutcome.Error];
 * 3. a body that is not an object, or whose `nodes` is not an array, is a [GraphOutcome.Error] —
 *    a truncated reply must not pass for a graph;
 * 4. nodes present but none readable is also a [GraphOutcome.Error], for the same reason;
 * 5. only then is it an [GraphOutcome.Ok] — an empty `nodes` array is a real, empty graph.
 *
 * The motifs are technical and stay untranslated (`SQLITE_DEPENDENCY_ERROR`, `HTTP 503`,
 * `UNREADABLE_BODY`): they are shown under a localised sentence, never in place of one.
 */
fun parseGraphResponse(status: Int, body: String?): GraphOutcome {
    val root = body
        ?.let { text -> runCatching { json.parseToJsonElement(text) }.getOrNull() }
        as? JsonObject

    // 1. The daemon's own "no database" answer, read before the status: 503 today, 200 later.
    if (root?.flag("available") == false) {
        return GraphOutcome.Unavailable(root.text("error") ?: "HTTP $status")
    }

    // 2. Any other non-2xx: the route failed, and its own message says how.
    if (status !in 200..299) return GraphOutcome.Error(root?.text("error") ?: "HTTP $status")

    // 3. A 2xx we cannot read is not an empty graph.
    if (root == null) return GraphOutcome.Error(GraphMotifs.UNREADABLE_BODY)
    val array = root["nodes"] as? JsonArray ?: return GraphOutcome.Error(GraphMotifs.NODES_NOT_AN_ARRAY)

    val nodes = array.mapNotNull { nodeOf(it) }

    // 4. A reply that carries nodes but none this client understands is a read failure.
    if (array.isNotEmpty() && nodes.isEmpty()) return GraphOutcome.Error(GraphMotifs.NO_READABLE_NODE)

    return GraphOutcome.Ok(nodes = nodes, links = linksOf(root, nodes))
}

/** One node, or null when this client cannot make a node out of it (it is then skipped). */
private fun nodeOf(element: JsonElement): GraphNode? = runCatching {
    val node = json.decodeFromJsonElement(GraphNode.serializer(), element)
    node.copy(
        label = node.label.ifBlank { node.fullLabel },
        fullLabel = node.fullLabel.ifBlank { node.label },
    )
}.getOrNull()?.takeIf { it.id.isNotBlank() }


/**
 * The route has been seen naming its link array both `links` and `edges` depending on the version,
 * so both are accepted. An end is either a bare id or an object carrying one (`{"id": …}`). Links
 * are directionless ([GraphLink]), so the two ends are sorted: the same pair named twice in
 * opposite directions stays one link, and the count on screen is true.
 */
private fun linksOf(root: JsonObject, nodes: List<GraphNode>): List<GraphLink> {
    val array = (root["links"] as? JsonArray) ?: (root["edges"] as? JsonArray) ?: return emptyList()
    val known = nodes.map { it.id }.toHashSet()
    val links = LinkedHashSet<GraphLink>()
    for (element in array) {
        val link = element as? JsonObject ?: continue
        val source = link.endpoint("source") ?: continue
        val target = link.endpoint("target") ?: continue
        if (source == target) continue
        if (source !in known || target !in known) continue
        links.add(if (source <= target) GraphLink(source, target) else GraphLink(target, source))
    }
    return links.toList()
}

private fun JsonObject.endpoint(key: String): String? = when (val element = this[key]) {
    is JsonPrimitive -> element.contentOrNull?.takeIf { it.isNotBlank() }
    is JsonObject -> (element["id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    else -> null
}

/** A string field, blank read as absent: `{"error": ""}` must not hide the status code. */
private fun JsonObject.text(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

/** The flag is a real JSON boolean; anything else is "the route said nothing". */
private fun JsonObject.flag(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.booleanOrNull
