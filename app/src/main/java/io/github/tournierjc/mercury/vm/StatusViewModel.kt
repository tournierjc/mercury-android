package io.github.tournierjc.mercury.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.tournierjc.mercury.AppContainer
import io.github.tournierjc.mercury.data.MercuryApi
import io.github.tournierjc.mercury.data.Notice
import io.github.tournierjc.mercury.data.dto.BrainStatusDto
import io.github.tournierjc.mercury.data.dto.HealthDto
import io.github.tournierjc.mercury.data.dto.ConfigDto
import io.github.tournierjc.mercury.data.dto.StatusDto
import io.github.tournierjc.mercury.data.dto.UsageDto
import io.github.tournierjc.mercury.data.toNotice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Everything the Status screen shows, each field optional.
 *
 * Defaults are the "not fetched yet" values rather than nulls with no fallback: the screen draws
 * its sections from this object while the first request is still in flight, so a fresh state must
 * already be drawable — empty lists, zero counters, no crash.
 */
data class StatusUiState(
    val loading: Boolean = true,
    val status: StatusDto? = null,
    val brain: BrainStatusDto? = null,
    val usage: UsageDto? = null,
    val config: ConfigDto? = null,
    /**
     * The daemon's own vitals — pid, loop p99, plugins — which the console used to show on its own
     * screen while this one showed the same state and uptime. One home for one fact.
     */
    val health: HealthDto? = null,
    /** The first failure, as a key the screen resolves — never a bare exception string. */
    val error: Notice? = null,
) {
    /** Providers the runtime could actually answer with: enabled and holding a key. */
    val readyProviders: List<String>
        get() = status?.providers.orEmpty()
            .filterValues { it.enabled && it.hasKey }
            .keys
            .toList()
}

/**
 * The dashboard: daemon state, provider wiring, the second brain, today's spend, and the identity
 * of the instance the app is attached to.
 *
 * `GET /api/status` is the only route whose failure is fatal — it is what the screen exists for.
 * The other three are best-effort: a daemon that has no brain endpoint must still show its uptime.
 */
class StatusViewModel(private val api: MercuryApi, private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(StatusUiState())
    val state: StateFlow<StatusUiState> = _state.asStateFlow()

    /** Where the app is attached — local configuration, not a route. */
    val serverUrl: String get() = container.serverUrl

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val status = api.status()
                val brain = runCatching { api.brainStatus() }.getOrNull()
                val usage = runCatching { api.usage() }.getOrNull()
                val config = runCatching { api.config() }.getOrNull()
                val health = runCatching { api.healthDetail() }.getOrNull()
                _state.update {
                    it.copy(loading = false, status = status, brain = brain, usage = usage, config = config, health = health)
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.toNotice()) }
            }
        }
    }

    /**
     * Restarts the daemon. The HTTP call returns before the socket actually drops, so the refresh
     * that follows is deliberately not awaited here — the screen reloads when the owner pulls.
     */
    fun restart() {
        viewModelScope.launch { runCatching { api.restart() } }
    }

    /** Stops the daemon: nothing to refresh afterwards, the socket is going away. */
    fun shutdown() {
        viewModelScope.launch { runCatching { api.shutdown() } }
    }

    fun clearBanner() {
        _state.update { it.copy(error = null) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { StatusViewModel(container.api, container) }
        }
    }
}
