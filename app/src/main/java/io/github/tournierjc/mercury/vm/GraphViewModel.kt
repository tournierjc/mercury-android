package io.github.tournierjc.mercury.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.tournierjc.mercury.AppContainer
import io.github.tournierjc.mercury.data.MercuryApi
import io.github.tournierjc.mercury.data.Notice
import io.github.tournierjc.mercury.data.NoticeKey
import io.github.tournierjc.mercury.data.dto.GraphNode
import io.github.tournierjc.mercury.data.dto.MemoryDto
import io.github.tournierjc.mercury.data.toNotice
import io.github.tournierjc.mercury.graph.GraphLink
import io.github.tournierjc.mercury.graph.GraphOutcome
import io.github.tournierjc.mercury.ui.graph.GraphEdge
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GraphUiState(
    val loading: Boolean = true,
    /** False when the route answered `available: false` — an absent graph, not an empty one. */
    val available: Boolean = true,
    /**
     * Why the graph is not exposed, as the route named it (`SQLITE_DEPENDENCY_ERROR` today).
     *
     * Technical on purpose: the screen shows it under the localised sentence, never in its place.
     * Null on every other branch, so a stale motif cannot survive a graph that came back.
     */
    val unavailableMotif: String? = null,
    val nodes: List<GraphNode> = emptyList(),
    val edges: List<GraphEdge> = emptyList(),
    val error: Notice? = null,
    /**
     * The node whose memory is being read, and the answer of `GET /api/brain/memory/{id}`.
     *
     * The graph payload carries only a label and a size; the summary, the scope, the evidence and
     * the dates live on the memory route, so the panel needs a second call. [detailId] says which
     * node [detail] belongs to — a refresh can change the selection under a reply in flight.
     */
    val detailId: String? = null,
    val detail: MemoryDto? = null,
    val detailLoading: Boolean = false,
    val detailError: Notice? = null,
)

/**
 * The brain's graph, as the daemon draws it and as [io.github.tournierjc.mercury.ui.graph.GraphLayout]
 * spreads it.
 *
 * The payload is not decoded through [io.github.tournierjc.mercury.data.dto.BeliefGraphDto] because
 * the route has been seen naming its link array both `links` and `edges` depending on the version,
 * and the daemon answers `503` with a body when SQLite is missing. Both are read in `:core`
 * — [io.github.tournierjc.mercury.graph.parseGraphResponse] — which turns the bytes into the three issues
 * this view model only has to route: a graph, an absent dependency, a failure. Keeping the read out
 * of the view model is what makes it testable: `:app` has no test source set.
 */
class GraphViewModel(private val api: MercuryApi) : ViewModel() {

    private val _state = MutableStateFlow(GraphUiState())
    val state: StateFlow<GraphUiState> = _state.asStateFlow()

    /** The read in flight: tapping a second node cancels the first, so no late reply wins. */
    private var detailJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching { api.brainGraph() }
                .onSuccess { outcome ->
                    // Three issues, never merged: a graph, an absent dependency with its motif,
                    // and a failure. The body of the 503 is read in :core (parseGraphResponse), so the
                    // "no database" answer reaches the screen with its cause instead of a status
                    // code in a banner — and an unreadable body never passes for an empty graph.
                    when (outcome) {
                        is GraphOutcome.Ok -> draw(outcome.nodes, outcome.links)
                        is GraphOutcome.Unavailable -> absent(outcome.motif)
                        is GraphOutcome.Error -> {
                            // The last answer that was actually read stays on screen, and the
                            // banner says this read failed. Nothing is cleared: an error is not
                            // proof that the brain is empty, nor that the graph is unexposed.
                            _state.update {
                                it.copy(
                                    loading = false,
                                    error = Notice(NoticeKey.REQUEST_REFUSED).withDetail(outcome.reason),
                                )
                            }
                        }
                    }
                }
                .onFailure { failure -> _state.update { it.copy(loading = false, error = failure.toNotice()) } }
        }
    }

    fun clearBanner() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Reads the memory behind a tapped node. The graph route gives a label and an importance; the
     * summary, the scope, the evidence and the dates are on the memory route, so this is a second
     * call — issued through [MercuryApi], which confines its HTTP work to `Dispatchers.IO`, so no
     * blocking read ever lands on the main thread.
     */
    fun readNode(id: String) {
        if (id.isBlank()) return
        detailJob?.cancel()
        _state.update { it.copy(detailId = id, detail = null, detailLoading = true, detailError = null) }
        detailJob = viewModelScope.launch {
            runCatching { api.memory(id) }
                .onSuccess { memory ->
                    _state.update {
                        if (it.detailId == id) it.copy(detail = memory, detailLoading = false) else it
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        if (it.detailId == id) {
                            it.copy(detailLoading = false, detailError = failure.toNotice())
                        } else {
                            it
                        }
                    }
                }
        }
    }

    /** Closes the panel and forgets the reply, so a reopened node never shows a stale memory. */
    fun clearDetail() {
        detailJob?.cancel()
        detailJob = null
        _state.update {
            it.copy(detailId = null, detail = null, detailLoading = false, detailError = null)
        }
    }

    /** The route drew a graph: it replaces what was on screen, panel included if its node is gone. */
    private fun draw(nodes: List<GraphNode>, links: List<GraphLink>) {
        _state.update { previous ->
            // A node that vanished from a refresh must not leave its memory in the panel.
            val kept = previous.detailId == null || nodes.any { it.id == previous.detailId }
            previous.copy(
                loading = false,
                available = true,
                unavailableMotif = null,
                nodes = nodes,
                edges = links.map { GraphEdge(it.source, it.target) },
                error = null,
                detailId = if (kept) previous.detailId else null,
                detail = if (kept) previous.detail else null,
                detailLoading = if (kept) previous.detailLoading else false,
                detailError = if (kept) previous.detailError else null,
            )
        }
    }

    /**
     * The route answered `available: false`: the graph is not exposed on this instance, and the
     * motif (`SQLITE_DEPENDENCY_ERROR` today) is technical — shown under the localised sentence,
     * with a Retry, because installing the dependency and retrying is a real fix.
     *
     * The drawing is cleared on purpose: a stale graph under an "unavailable" banner would say the
     * opposite of what the route just said.
     */
    private fun absent(motif: String) {
        detailJob?.cancel()
        _state.update {
            it.copy(
                loading = false,
                available = false,
                unavailableMotif = motif,
                nodes = emptyList(),
                edges = emptyList(),
                error = null,
                detailId = null,
                detail = null,
                detailLoading = false,
                detailError = null,
            )
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { GraphViewModel(container.api) }
        }
    }
}
