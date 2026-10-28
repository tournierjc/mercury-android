package io.github.tournierjc.mercury.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.tournierjc.mercury.AppContainer
import io.github.tournierjc.mercury.data.AuthException
import io.github.tournierjc.mercury.data.MercuryApi
import io.github.tournierjc.mercury.data.dto.BeliefDto
import io.github.tournierjc.mercury.data.dto.BeliefGraphDto
import io.github.tournierjc.mercury.data.dto.ConfigDto
import io.github.tournierjc.mercury.data.dto.MemoryDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import io.github.tournierjc.mercury.data.Notice
import io.github.tournierjc.mercury.data.NoticeKey
import io.github.tournierjc.mercury.data.toNotice

enum class MemoryTab { Memories, Beliefs, Quarantine, About }

data class MemoryUiState(
    val loading: Boolean = true,
    val tab: MemoryTab = MemoryTab.Memories,
    val query: String = "",
    val memories: List<MemoryDto> = emptyList(),
    val total: Int = 0,
    val beliefs: List<BeliefDto> = emptyList(),
    val persons: List<JsonObject> = emptyList(),
    val quarantined: List<MemoryDto> = emptyList(),
    val graph: BeliefGraphDto? = null,
    val config: ConfigDto? = null,
    val busy: Boolean = false,
    /** What the last finished action says (a key, resolved by the screen). */
    val message: Notice? = null,
    val error: Notice? = null,
)

/**
 * The agent's second brain seen from the phone: what it remembers, what it
 * believes, what it set aside, and the runtime identity behind it.
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
                val query = _state.value.query
                val memories = api.memories(query = query.ifBlank { null })
                val beliefs = runCatching { api.beliefs() }.getOrNull()
                val persons = runCatching { api.persons() }.getOrNull()
                val quarantined = runCatching { api.quarantine() }.getOrNull()
                val graph = runCatching { api.beliefGraph() }.getOrNull()
                val config = runCatching { api.config() }.getOrNull()
                _state.update {
                    it.copy(
                        loading = false,
                        memories = memories.memories,
                        total = memories.total,
                        beliefs = beliefs?.beliefs.orEmpty(),
                        persons = persons?.persons.orEmpty(),
                        quarantined = quarantined?.memories.orEmpty(),
                        graph = graph,
                        config = config,
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
            runCatching { api.memories(query = query.ifBlank { null }) }
                .onSuccess { page ->
                    _state.update { it.copy(memories = page.memories, total = page.total) }
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
                    _state.update { s ->
                        s.copy(memories = s.memories.filterNot { it.id == id }, message = Notice(NoticeKey.MEMORY_FORGOTTEN))
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
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { MemoryViewModel(container.api, container) }
        }
    }
}
