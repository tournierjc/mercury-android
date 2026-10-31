package io.github.tournierjc.mercury.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.tournierjc.mercury.AppContainer
import io.github.tournierjc.mercury.data.toNotice
import androidx.lifecycle.viewModelScope
import io.github.tournierjc.mercury.data.MercuryApi
import io.github.tournierjc.mercury.data.dto.ConnectFlowDto
import io.github.tournierjc.mercury.data.dto.InstallRecordDto
import io.github.tournierjc.mercury.data.dto.SandboxToolDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Tools the bots can run, their sign-in state, and the two long operations behind them.
 *
 * An install is a job on the daemon (a release tarball, a venv), and a device flow ends when the owner
 * finishes it on another screen — so this holds the job and the flow, and the screen polls while
 * either is still moving. Nothing is assumed: the state comes back from the daemon each time.
 */
data class SandboxToolsState(
    val tools: List<SandboxToolDto> = emptyList(),
    val installs: Map<String, InstallRecordDto> = emptyMap(),
    val flows: Map<String, ConnectFlowDto> = emptyMap(),
    val loading: Boolean = false,
    /** The tool an action is in flight for, so one row spins instead of the whole list. */
    val busy: String = "",
    val error: String = "",
    /** True until the first answer: an empty catalogue and "no tools" are different things. */
    val loaded: Boolean = false,
)

class SandboxToolsViewModel(private val api: MercuryApi) : ViewModel() {

    private val _state = MutableStateFlow(SandboxToolsState())
    val state: StateFlow<SandboxToolsState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = "") }
            runCatching { api.sandboxTools() }
                .onSuccess { tools -> _state.update { it.copy(tools = tools, loading = false, loaded = true) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.readable(), loaded = true) } }
        }
    }

    /** Starts an install; the daemon answers with the job, and [poll] watches it. */
    fun install(id: String) {
        if (id.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = id, error = "") }
            runCatching { api.installSandboxTool(id) }
                .onSuccess { record ->
                    _state.update { it.copy(installs = it.installs + (id to record), busy = "") }
                }
                .onFailure { e -> _state.update { it.copy(busy = "", error = e.readable()) } }
        }
    }

    /** Starts a device flow and keeps the code the owner has to type. */
    fun connect(id: String) {
        if (id.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = id, error = "") }
            runCatching { api.connectSandboxTool(id) }
                .onSuccess { flow ->
                    _state.update { it.copy(flows = it.flows + (id to flow), busy = "") }
                }
                .onFailure { e -> _state.update { it.copy(busy = "", error = e.readable()) } }
        }
    }

    /**
     * One turn of the polling loop: follow every running install and every pending flow, then re-read
     * the catalogue — a tool that finished installing is now present, and one whose flow landed is now
     * connected. Nothing polls when nothing is moving, so an idle screen costs nothing.
     */
    fun poll() {
        val current = _state.value
        val running = current.installs.values.filter { it.state == "running" }
        val pending = current.flows.values.filter { it.state == "pending" }
        if (running.isEmpty() && pending.isEmpty()) return
        viewModelScope.launch {
            var refresh = false
            for (record in running) {
                runCatching { api.sandboxToolInstall(record.id) }.getOrNull()?.let { fresh ->
                    _state.update { it.copy(installs = it.installs + (fresh.id to fresh)) }
                    if (fresh.state != "running") refresh = true
                }
            }
            for (flow in pending) {
                runCatching { api.sandboxToolConnect(flow.id) }.getOrNull()?.let { fresh ->
                    _state.update { it.copy(flows = it.flows + (fresh.id to fresh)) }
                    if (fresh.state != "pending") refresh = true
                }
            }
            if (refresh) runCatching { api.sandboxTools() }.getOrNull()?.let { tools ->
                _state.update { it.copy(tools = tools) }
            }
        }
    }

    /** Signs a tool out; the row's state comes back from the daemon, never assumed. */
    fun disconnect(id: String) {
        if (_state.value.busy.isNotBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = id, error = "") }
            runCatching { api.disconnectSandboxTool(id) }
                .onFailure { e -> _state.update { it.copy(error = e.readable()) } }
            refresh()
        }
    }

    fun clearError() = _state.update { it.copy(error = "") }

    private fun Throwable.readable(): String = toNotice().toString()

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SandboxToolsViewModel(container.api) }
        }
    }
}
