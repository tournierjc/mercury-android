package io.github.tournierjc.mercury.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.tournierjc.mercury.AppContainer
import io.github.tournierjc.mercury.data.MercuryApi
import io.github.tournierjc.mercury.data.dto.BeliefDto
import io.github.tournierjc.mercury.data.dto.MemoryDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import io.github.tournierjc.mercury.data.Notice
import io.github.tournierjc.mercury.data.NoticeKey
import io.github.tournierjc.mercury.data.toNotice

/**
 * The four sections the webapp shows, in its order. `Memories` used to be a merged list of both
 * scopes and `About` a tab of runtime facts: the scopes are two different memories of the world
 * (what the agent calls on, what it keeps below) and the runtime facts belong to the Status screen.
 */
enum class MemoryTab { Conscious, Subconscious, Beliefs, Quarantine }

data class MemoryUiState(
    val loading: Boolean = true,
    val tab: MemoryTab = MemoryTab.Conscious,
    val query: String = "",
    /** What the brain keeps at hand. */
    val conscious: List<MemoryDto> = emptyList(),
    val consciousTotal: Int = 0,
    /** What it only reaches by association. */
    val subconscious: List<MemoryDto> = emptyList(),
    val subconsciousTotal: Int = 0,
    val beliefs: List<BeliefDto> = emptyList(),
    val quarantined: List<MemoryDto> = emptyList(),
    val busy: Boolean = false,
    /** What the last finished action says (a key, resolved by the screen). */
    val message: Notice? = null,
    val error: Notice? = null,
)

/**
 * The agent's second brain seen from the phone: what it remembers (both scopes), what it believes,
 * and what it set aside.
 */
class MemoryViewModel(
    private val api: MercuryApi,
    private val container: AppContainer,
) : ViewModel() {

    private val _state = MutableStateFlow(MemoryUiState())
    val state: StateFlow<MemoryUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val query = _state.value.query.ifBlank { null }
                val conscious = api.memories(query = query, scope = CONSCIOUS)
                // The three secondary lists are allowed to fail on their own: a brain whose beliefs
                // route is off still shows its memories.
                val subconscious = runCatching { api.memories(query = query, scope = SUBCONSCIOUS) }.getOrNull()
                val beliefs = runCatching { api.beliefs() }.getOrNull()
                val quarantined = runCatching { api.quarantine() }.getOrNull()
                _state.update {
                    it.copy(
                        loading = false,
                        conscious = conscious.memories,
                        consciousTotal = conscious.total,
                        subconscious = subconscious?.memories.orEmpty(),
                        subconsciousTotal = subconscious?.total ?: 0,
                        beliefs = beliefs?.beliefs.orEmpty(),
                        quarantined = quarantined?.memories.orEmpty(),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.readable()) }
            }
        }
    }

    fun selectTab(tab: MemoryTab) {
        _state.update { it.copy(tab = tab) }
    }

    /** Debounced so typing in the search field does not hammer the daemon. */
    fun search(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            runCatching {
                val conscious = api.memories(query = query.ifBlank { null }, scope = CONSCIOUS)
                val subconscious = api.memories(query = query.ifBlank { null }, scope = SUBCONSCIOUS)
                conscious to subconscious
            }
                .onSuccess { (conscious, subconscious) ->
                    _state.update {
                        it.copy(
                            conscious = conscious.memories,
                            consciousTotal = conscious.total,
                            subconscious = subconscious.memories,
                            subconsciousTotal = subconscious.total,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(error = e.readable()) } }
        }
    }

    fun addMemory(type: String, summary: String, detail: String?) {
        if (summary.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null, message = null) }
            runCatching { api.addMemory(type, summary.trim(), detail?.trim()?.ifBlank { null }) }
                .onSuccess { _state.update { s -> s.copy(busy = false, message = Notice(NoticeKey.MEMORY_ADDED)) }; refresh() }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    fun forgetMemory(id: String) {
        viewModelScope.launch {
            runCatching { api.forgetMemory(id) }
                .onSuccess {
                    // The row can be in either scope; both are cleaned so the list the owner is
                    // looking at never keeps a memory the brain has dropped.
                    _state.update { s ->
                        s.copy(
                            conscious = s.conscious.filterNot { it.id == id },
                            subconscious = s.subconscious.filterNot { it.id == id },
                            message = Notice(NoticeKey.MEMORY_FORGOTTEN),
                        )
                    }
                }
                .onFailure { e -> _state.update { s -> s.copy(error = e.readable()) } }
        }
    }

    fun editMemory(id: String, summary: String, importance: Double?) {
        viewModelScope.launch {
            runCatching {
                api.updateMemory(
                    id,
                    io.github.tournierjc.mercury.data.dto.UpdateMemoryRequest(
                        summary = summary.trim().ifBlank { null },
                        importance = importance,
                    ),
                )
            }
                .onSuccess { _state.update { s -> s.copy(message = Notice(NoticeKey.MEMORY_UPDATED)) }; refresh() }
                .onFailure { e -> _state.update { s -> s.copy(error = e.readable()) } }
        }
    }

    fun confirmBelief(id: String) = beliefAction(Notice(NoticeKey.BELIEF_CONFIRMED)) { api.confirmBelief(id) }

    fun forgetBelief(id: String) = beliefAction(Notice(NoticeKey.BELIEF_FORGOTTEN)) { api.forgetBelief(id) }

    fun correctBelief(id: String, statement: String) {
        if (statement.trim().length < 3) return
        beliefAction(Notice(NoticeKey.BELIEF_CORRECTED)) { api.correctBelief(id, statement.trim()) }
    }

    fun restoreQuarantined(id: String) = beliefAction(Notice(NoticeKey.MEMORY_RESTORED)) { api.restoreQuarantined(id) }

    fun clearBanner() {
        _state.update { it.copy(message = null, error = null) }
    }

    private fun beliefAction(success: Notice, block: suspend () -> Any?) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null, error = null) }
            runCatching { block() }
                .onSuccess { _state.update { s -> s.copy(busy = false, message = success) }; refresh() }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    /** One mapping for the whole app (see `toNotice`), no local copy of the wording. */
    private fun Throwable.readable(): Notice = toNotice()

    companion object {
        private const val CONSCIOUS = "conscious"
        private const val SUBCONSCIOUS = "subconscious"

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { MemoryViewModel(container.api, container) }
        }
    }
}
