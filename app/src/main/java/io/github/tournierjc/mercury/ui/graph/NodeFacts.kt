package io.github.tournierjc.mercury.ui.graph

import io.github.tournierjc.mercury.data.dto.MemoryDto

/**
 * What the node panel prints under its title.
 *
 * The panel used to print the node's label as the title and then the memory's summary as its body,
 * and on this fleet those are the same sentence — the graph draws `fullLabel` straight from the
 * summary. So tapping a node showed its description twice, which reads as a rendering fault rather
 * than as two facts.
 *
 * The detail text is kept only when it says something the title does not.
 */
fun nodeBody(title: String, summary: String, detail: String?): List<String> {
    val seenTitle = normalise(title)
    return listOf(summary, detail.orEmpty())
        .map { it.trim() }
        .filter { it.isNotBlank() && normalise(it) != seenTitle }
        .distinctBy { normalise(it) }
}

/**
 * The facts the web app shows for a node, in the order it shows them.
 *
 * The phone only showed the sentence and a weight, so scope, evidence, source and the dates were
 * visible in the browser and nowhere on the device — the same memory described two different ways.
 * A zero here means "the server said nothing", not "zero", so the panel can leave the row out
 * instead of printing a claim the record never made.
 */
data class NodeFacts(
    val scope: String?,
    val importancePercent: Int?,
    val confidencePercent: Int?,
    val evidence: Int?,
    val connections: Int,
    val source: String?,
    val created: Long?,
    val updated: Long?,
    val lastSeen: Long?,
    val id: String,
)

fun factsOf(memory: MemoryDto, connections: Int): NodeFacts = NodeFacts(
    scope = memory.scope.trim().ifBlank { null },
    importancePercent = percent(memory.importance),
    confidencePercent = percent(memory.confidence),
    evidence = memory.evidenceCount.takeIf { it > 0 },
    connections = connections.coerceAtLeast(0),
    source = memory.source.trim().ifBlank { null },
    created = memory.createdAt.takeIf { it > 0 },
    updated = memory.updatedAt.takeIf { it > 0 },
    lastSeen = memory.lastSeenAt.takeIf { it > 0 },
    id = memory.id,
)

/** `0.5` → `50`. A weight of 0 is the server saying nothing, so it stays null. */
private fun percent(value: Double): Int? =
    if (value <= 0.0 || value.isNaN()) null else (value * 100).toInt().coerceIn(0, 100)

/** Case, spacing and trailing punctuation do not make a second sentence. */
private fun normalise(text: String): String =
    text.trim().lowercase().replace(Regex("\\s+"), " ").trimEnd('.', '!', '?', '…')
