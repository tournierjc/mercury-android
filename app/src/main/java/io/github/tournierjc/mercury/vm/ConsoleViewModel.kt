package io.github.tournierjc.mercury.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.tournierjc.mercury.AppContainer
import io.github.tournierjc.mercury.data.MercuryApi
import io.github.tournierjc.mercury.data.NowPlaying
import io.github.tournierjc.mercury.data.Notice
import io.github.tournierjc.mercury.data.NoticeKey
import io.github.tournierjc.mercury.data.SpotifyDevice
import io.github.tournierjc.mercury.data.SpotifyJson
import io.github.tournierjc.mercury.data.dto.SpotifyStatusDto
import io.github.tournierjc.mercury.data.dto.StatusDto
import io.github.tournierjc.mercury.data.toNotice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The host screen: the daemon's playback, and the two verbs that stop or restart it.
 *
 * It used to be a second copy of nine other screens — the deliverables, the cost, the disk, the
 * terminal, git, the tasks, the schedules, the agents, the skills, the plugins, MCP and the
 * workspace, each loaded here to be drawn a second time. Every one of those has a destination of
 * its own now, and this screen reads only what nobody else shows.
 */
data class ConsoleUiState(
    val loading: Boolean = true,
    val status: StatusDto? = null,
    val busy: Boolean = false,
    val message: Notice? = null,
    val error: Notice? = null,
    // Spotify: a convenience surface, not a music app.
    val music: SpotifyStatusDto? = null,
    val nowPlaying: NowPlaying? = null,
    val devices: List<SpotifyDevice> = emptyList(),
    val musicBusy: Boolean = false,
)

class ConsoleViewModel(private val api: MercuryApi, container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ConsoleUiState())
    val state: StateFlow<ConsoleUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** The daemon's state, for the header's subtitle, and the player's own routes. */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { api.status() }
                .onSuccess { status -> _state.update { it.copy(loading = false, status = status) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.toNotice()) } }
            refreshMusic()
        }
    }

    fun clearBanner() = _state.update { it.copy(message = null, error = null) }

    /** Reads what Spotify answers; silent when the daemon has no Spotify at all. */
    fun refreshMusic() {
        viewModelScope.launch {
            val status = runCatching { api.spotifyStatus() }.getOrNull()
            val line = runCatching { api.spotifyNowPlaying() }.getOrNull()
            val devices = runCatching { api.spotifyDevices() }.getOrDefault(emptyList())
            _state.update { it.copy(music = status, nowPlaying = SpotifyJson.nowPlaying(line?.text), devices = devices) }
        }
    }

    private fun musicAction(block: suspend () -> Any?) {
        viewModelScope.launch {
            _state.update { it.copy(musicBusy = true) }
            runCatching { block() }
                .onFailure { e ->
                    _state.update {
                        it.copy(error = Notice(NoticeKey.MUSIC_ACTION_FAILED).withDetail(e.message))
                    }
                }
            _state.update { it.copy(musicBusy = false) }
            refreshMusic()
        }
    }

    fun play() = musicAction { api.spotifyPlay() }
    fun pause() = musicAction { api.spotifyPause() }
    fun next() = musicAction { api.spotifyNext() }
    fun previous() = musicAction { api.spotifyPrevious() }
    fun setVolume(volume: Int) = musicAction { api.spotifyVolume(volume) }
    fun setShuffle(state: Boolean) = musicAction { api.spotifyShuffle(state) }

    /**
     * Restarts the daemon. The HTTP call returns before the socket actually drops, so there is
     * nothing honest to reload here — the screen says it asked.
     */
    fun restart() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runCatching { api.restart() }
                .onSuccess { _state.update { s -> s.copy(busy = false, message = Notice(NoticeKey.DAEMON_RESTART_REQUESTED)) } }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.toNotice()) } }
        }
    }

    /** Stops the daemon: nothing to refresh afterwards, the socket is going away. */
    fun shutdown() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runCatching { api.shutdown() }
                .onSuccess { _state.update { s -> s.copy(busy = false) } }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.toNotice()) } }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ConsoleViewModel(container.api, container) }
        }
    }
}
