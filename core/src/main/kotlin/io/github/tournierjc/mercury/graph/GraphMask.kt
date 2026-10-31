package io.github.tournierjc.mercury.graph

import io.github.tournierjc.mercury.data.dto.GraphNode

/**
 * The type legend of the brain graph, as a filter.
 *
 * The legend is an axis on top of a drawing, not a new drawing: a chip hides the nodes of its type
 * and the links that touch them, and that is all. The positions are computed once on the whole set
 * — [GraphLayout] runs on `state.nodes` — so turning a chip over never moves a node that is still
 * on screen, and zoom and pan survive untouched. That constraint is why the filter lives here, in
 * `:core`: "the filter masks, it does not relaunch" is a rule a JVM test can hold, and an
 * implementation that recomputes would fail it.
 *
 * A type is a daemon value, never a label: the chips are built from what the graph actually
 * carries, so a new type cannot be filtered by a string this client made up.
 */
object GraphMask {

    /**
     * The types present in the graph, in the daemon's reference order.
     *
     * A type absent from the drawing gets no chip — a chip whose only possible effect is to hide
     * nothing is a control that lies about what it does. Types the reference list does not know
     * come last, alphabetically, so a newer daemon still shows them instead of dropping them.
     */
    fun presentTypes(nodes: List<GraphNode>, referenceOrder: List<String>): List<String> {
        val carried = nodes.mapNotNullTo(LinkedHashSet()) { node -> node.type.takeIf { it.isNotBlank() } }
        val ordered = referenceOrder.filter { it in carried }
        val unknown = carried.filterNot { it in referenceOrder }.sorted()
        return ordered + unknown
    }

    /**
     * Turns one chip over, or back.
     *
     * The mask is the set of hidden types. Switching the last lit chip off would leave the canvas
     * empty and the only way out would be a control that just disappeared — so instead it clears
     * the mask and the row reads "Tous" again. All types hidden is therefore unreachable, which is
     * why no "everything is filtered out" message exists: an unreachable state does not get a
     * string.
     */
    fun toggle(hidden: Set<String>, type: String, presentTypes: List<String>): Set<String> {
        if (type.isBlank()) return hidden
        val next = if (type in hidden) hidden - type else hidden + type
        val everythingHidden = presentTypes.isNotEmpty() && presentTypes.all { it in next }
        return if (everythingHidden) emptySet() else next
    }

    /** The ids of the nodes a mask hides: links are drawn by id, the mask is held by type. */
    fun hiddenIds(nodes: List<GraphNode>, hidden: Set<String>): Set<String> =
        if (hidden.isEmpty()) emptySet() else nodes.mapNotNullTo(HashSet()) { node ->
            node.id.takeIf { node.type in hidden }
        }

    fun visibleNodes(nodes: List<GraphNode>, hiddenIds: Set<String>): List<GraphNode> =
        if (hiddenIds.isEmpty()) nodes else nodes.filterNot { it.id in hiddenIds }

    /**
     * The links still drawn: a link is a line between two nodes, so it goes with either end.
     *
     * A link left behind by a hidden node would point at nothing — a stroke to the middle of an
     * empty spot reads as a bug, not as a filter.
     */
    fun visibleLinks(links: List<GraphLink>, hiddenIds: Set<String>): List<GraphLink> =
        if (hiddenIds.isEmpty()) links
        else links.filterNot { it.source in hiddenIds || it.target in hiddenIds }

    /**
     * The degree of every node — how many links touch it — in one pass.
     *
     * Counted on the whole set, never on the visible one: a node's degree is a property of the
     * memory, and the sheet that shows it must not change number because a chip is off. Computed
     * once per graph in the view model: done inside the sheet's composition it would be recounted
     * on every scroll frame.
     */
    fun degrees(links: List<GraphLink>): Map<String, Int> {
        val counted = HashMap<String, Int>(links.size * 2)
        for (link in links) {
            counted[link.source] = (counted[link.source] ?: 0) + 1
            counted[link.target] = (counted[link.target] ?: 0) + 1
        }
        return counted
    }
}
