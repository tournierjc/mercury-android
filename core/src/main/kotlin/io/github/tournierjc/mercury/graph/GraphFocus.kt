package io.github.tournierjc.mercury.graph

import java.text.Normalizer
import java.util.Locale

/**
 * One link of the brain graph, by node id. The brain draws its links without direction, so the two
 * ends are interchangeable here even though the payload names them `source` and `target`.
 */
data class GraphLink(val source: String, val target: String)

/**
 * A node as the filter box and the legend read it: the words a person can type, the type the server
 * filed it under, and the colour the server drew it with.
 */
data class FocusNode(
    val id: String,
    val label: String = "",
    val fullLabel: String = "",
    val type: String = "",
    val color: String = "",
)

/** One row of the legend: a type the graph really contains, and the colour of its nodes. */
data class LegendEntry(val type: String, val color: String)

/** How loudly a node is drawn while a filter or a selection is active. */
enum class NodeEmphasis { FULL, DIMMED, FAINT }

/**
 * What the graph's filter box keeps, and what a selected node leaves in the shade.
 *
 * The webapp decided both on the canvas: a query dimmed every node whose text did not match, and a
 * selected node dimmed everything that was neither itself nor a direct neighbour. The phone has no
 * hover, so the same two rules are settled here — once, and with a test — while the canvas only
 * applies the shade.
 */
data class GraphFocus(
    val query: String = "",
    val focusId: String? = null,
    val matches: Set<String> = emptySet(),
    val neighbours: Set<String> = emptySet(),
) {
    /** True when a query is typed: the filter then wins over the selection. */
    val filtering: Boolean get() = query.isNotBlank()

    fun emphasisOf(id: String): NodeEmphasis = when {
        filtering -> if (id in matches) NodeEmphasis.FULL else NodeEmphasis.FAINT
        focusId != null -> if (id == focusId || id in neighbours) NodeEmphasis.FULL else NodeEmphasis.DIMMED
        else -> NodeEmphasis.FULL
    }

    companion object {
        /** Neither a query nor a selection: every node at full strength. */
        val none = GraphFocus()

        fun of(
            nodes: List<FocusNode>,
            links: List<GraphLink>,
            query: String,
            focusId: String? = null,
        ): GraphFocus {
            val selected = focusId?.takeIf { it.isNotBlank() }
            return GraphFocus(
                query = query,
                focusId = selected,
                matches = if (query.isBlank()) emptySet() else matchingIds(nodes, query),
                neighbours = selected?.let { neighboursOf(links, it) } ?: emptySet(),
            )
        }

        /** Ids whose label or full label holds [query], accents and case ignored. */
        fun matchingIds(nodes: List<FocusNode>, query: String): Set<String> {
            val needle = fold(query)
            if (needle.isEmpty()) return emptySet()
            val found = LinkedHashSet<String>()
            for (node in nodes) {
                if (fold(node.label).contains(needle) || fold(node.fullLabel).contains(needle)) {
                    found.add(node.id)
                }
            }
            return found
        }

        /** The ids a memory is directly linked to: a link is a tie, not an arrow. */
        fun neighboursOf(links: List<GraphLink>, id: String): Set<String> {
            val found = LinkedHashSet<String>()
            for (link in links) {
                if (link.source == id && link.target != id) found.add(link.target)
                if (link.target == id && link.source != id) found.add(link.source)
            }
            return found
        }

        /** How many links touch [id]; a self-link counts once, as the payload writes it once. */
        fun connections(links: List<GraphLink>, id: String): Int =
            links.count { it.source == id || it.target == id }

        /**
         * The types the graph really contains, in first-seen order, each with the colour its nodes
         * carry. A type whose first node came without a colour takes the first colour its later
         * nodes were given; a type no node carries never appears — the legend describes the drawing,
         * it does not list a palette.
         */
        fun legend(nodes: List<FocusNode>): List<LegendEntry> {
            val colours = LinkedHashMap<String, String>()
            for (node in nodes) {
                val type = node.type.trim()
                if (type.isEmpty()) continue
                val colour = node.color.trim()
                val known = colours[type]
                if (known == null) colours[type] = colour
                else if (known.isEmpty() && colour.isNotEmpty()) colours[type] = colour
            }
            return colours.map { LegendEntry(it.key, it.value) }
        }

        /** Lower case, without the diacritics a phone keyboard rarely offers. */
        fun fold(text: String): String =
            Normalizer.normalize(text.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(COMBINING_MARKS, "")

        private val COMBINING_MARKS = Regex("\\p{Mn}+")
    }
}
