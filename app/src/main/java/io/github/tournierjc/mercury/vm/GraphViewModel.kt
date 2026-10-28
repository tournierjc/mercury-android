package io.github.tournierjc.mercury.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.tournierjc.mercury.AppContainer
import io.github.tournierjc.mercury.data.MercuryApi
import io.github.tournierjc.mercury.data.Notice
import io.github.tournierjc.mercury.data.dto.GraphNode
import io.github.tournierjc.mercury.data.dto.MemoryDto
import io.github.tournierjc.mercury.data.toNotice
import io.github.tournierjc.mercury.ui.graph.GraphEdge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

data class GraphUiState(
    val loading: Boolean = true,
    /** False when the route answered `available: false` — an absent graph, not an empty one. */
    val available: Boolean = true,
    val nodes: List<GraphNode> = emptyList(),
    val edges: List<GraphEdge> = emptyList(),
    val error: Notice? = null,
    /** The node whose memory is being read, empty when no panel is open. */
    val detailId: String = "",
    /** The memory behind the open panel: the panel shows what the record says, not a summary of it. */
    val detailMemory: MemoryDto? = null,
    val detailLoading: Boolean = false,
    val detailFailed: Boolean = false,
    /** What the search field holds: the drawn graph is filtered by it. */
    val query: String = "",
)

/**
 * The brain's graph, as the daemon draws it and as [io.github.tournierjc.mercury.ui.graph.GraphLayout]
 * spreads it.
 *
 * The payload is read as raw JSON rather than through [io.github.tournierjc.mercury.data.dto.BeliefGraphDto]
 * for one reason: the route has been seen naming its link array both `links` and `edges`
 * depending on the version, and the DTO only models one of them. Reading the object lets the
 * screen accept either without a second round-trip, and an endpoint named by an object
 * (`{"source":{"id":…}}`) is resolved the same way as a bare id.
 */
class GraphViewModel(private val api: MercuryApi) : ViewModel() {

    private val _state = MutableStateFlow(GraphUiState())
    val state: StateFlow<GraphUiState> = _state.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching { api.getJson("/api/brain/graph") }
                .onSuccess { raw ->
                    val nodes = nodesOf(raw)
                    _state.update {
                        it.copy(
                            loading = false,
                            available = (raw["available"] as? JsonPrimitive)?.booleanOrNull ?: true,
                            nodes = nodes,
                            edges = edgesOf(raw, nodes),
                            error = null,
                        )
                    }
                }
                .onFailure { failure -> _state.update { it.copy(loading = false, error = failure.toNotice()) } }
        }
    }

    /**
     * Read the memory behind a node. The webapp fetched it on click too — a graph that shows you a
     * shape and refuses to say what it is has done half the work.
     */
    fun selectNode(id: String) {
        _state.update {
            it.copy(detailId = id, detailMemory = null, detailLoading = true, detailFailed = false)
        }
        viewModelScope.launch {
            runCatching { api.memory(id) }
                .onSuccess { memory ->
                    _state.update {
                        if (it.detailId != id) {
                            it
                        } else {
                            it.copy(detailLoading = false, detailMemory = memory)
                        }
                    }
                }
                .onFailure {
                    _state.update { if (it.detailId == id) it.copy(detailLoading = false, detailFailed = true) else it }
                }
        }
    }

    fun clearSelection() {
        _state.update { it.copy(detailId = "", detailMemory = null, detailLoading = false, detailFailed = false) }
    }

    fun search(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun clearBanner() {
        _state.update { it.copy(error = null) }
    }

    private fun nodesOf(raw: JsonObject): List<GraphNode> {
        val array = raw["nodes"] as? JsonArray ?: return emptyList()
        val nodes = ArrayList<GraphNode>(array.size)
        for (element in array) {
            // A node the app cannot read is skipped, never rendered as a blank circle.
            val node = runCatching { json.decodeFromJsonElement(GraphNode.serializer(), element) }.getOrNull()
            if (node != null && node.id.isNotBlank()) nodes.add(node)
        }
        return nodes
    }

    private fun edgesOf(raw: JsonObject, nodes: List<GraphNode>): List<GraphEdge> {
        val array = (raw["links"] as? JsonArray) ?: (raw["edges"] as? JsonArray) ?: return emptyList()
        val known = nodes.map { it.id }.toHashSet()
        val edges = LinkedHashSet<GraphEdge>()
        for (element in array) {
            val link = element as? JsonObject ?: continue
            val source = link.endpoint("source") ?: continue
            val target = link.endpoint("target") ?: continue
            if (source == target) continue
            if (source !in known || target !in known) continue
            edges.add(GraphEdge(source, target))
        }
        return edges.toList()
    }

    /** An end is either a bare id or an object carrying one (`{"id": …}`). */
    private fun JsonObject.endpoint(key: String): String? = when (val element = this[key]) {
        is JsonPrimitive -> element.contentOrNull
        is JsonObject -> (element["id"] as? JsonPrimitive)?.contentOrNull
        else -> null
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { GraphViewModel(container.api) }
        }
    }
}
