package io.github.tournierjc.mercury.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.tournierjc.mercury.data.BotJson
import io.github.tournierjc.mercury.data.Notice
import io.github.tournierjc.mercury.data.NoticeKey
import io.github.tournierjc.mercury.data.dto.DeliverableDto
import io.github.tournierjc.mercury.AppContainer
import io.github.tournierjc.mercury.data.MercuryApi
import io.github.tournierjc.mercury.util.OutputActions
import io.github.tournierjc.mercury.util.OutputPreviewState
import io.github.tournierjc.mercury.util.SharedFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import io.github.tournierjc.mercury.data.toNotice
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * The fleet's deliverables on their own screen.
 *
 * The Livrables screen used to borrow the console's view model, which meant it also loaded the
 * runtime, the health route, the schedules, the skills and the plugins to draw a file list. It reads
 * what it draws: the fleet's outputs, the roster's faces, and the three actions a file has.
 */
data class DeliverablesUiState(
    val loading: Boolean = true,
    val outputs: List<DeliverableDto> = emptyList(),
    val busy: Boolean = false,
    val error: Notice? = null,
    val message: Notice? = null,
    val outputPreview: OutputPreviewState? = null,
    val pendingShare: SharedFile? = null,
)

class DeliverablesViewModel(private val api: MercuryApi) : ViewModel() {

    private val _state = MutableStateFlow(DeliverablesUiState())
    val state: StateFlow<DeliverablesUiState> = _state.asStateFlow()

    private val _avatars = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    val avatars: StateFlow<Map<String, ByteArray>> = _avatars.asStateFlow()
    private val avatarInFlight = mutableSetOf<String>()
    /** Deliverable actions, the same ones the bot sheet uses, so a file behaves the same. */
    private val outputActions = OutputActions(api)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { api.fleetOutputs() }
                .onSuccess { page ->
                    _state.update { it.copy(loading = false, outputs = page.outputs) }
                    // Faces come from the roster: a group is headed by a bot, and a monogram is the
                    // fallback when the bot has no picture.
                    val bots = runCatching { api.bots() }.getOrNull()?.bots.orEmpty()
                    loadAvatars(bots)
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.readable()) } }
        }
    }

    /** Fetches only the faces the roster says exist, once per bot. */
    fun loadAvatars(bots: List<JsonObject>) {
        bots.filter { BotJson.hasAvatar(it) }
            .map { BotJson.id(it) }
            .filter { id -> id.isNotBlank() && _avatars.value[id] == null && avatarInFlight.add(id) }
            .forEach { id ->
                viewModelScope.launch {
                    runCatching { api.botAvatar(id) }
                        .onSuccess { bytes -> if (bytes != null) _avatars.update { it + (id to bytes) } }
                    avatarInFlight.remove(id)
                }
            }
    }

    fun previewOutput(output: DeliverableDto) {
        if (output.botId.isBlank() || output.name.isBlank()) return
        _state.update { it.copy(outputPreview = OutputPreviewState(output, loading = true)) }
        viewModelScope.launch {
            val preview = outputActions.preview(output)
            _state.update { it.copy(outputPreview = preview) }
        }
    }

    fun dismissPreview() = _state.update { it.copy(outputPreview = null) }

    fun shareOutput(output: DeliverableDto) {
        if (output.botId.isBlank() || output.name.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { outputActions.download(output) }
                .onSuccess { file -> _state.update { it.copy(busy = false, pendingShare = file) } }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.readable()) } }
        }
    }

    fun consumeShare() = _state.update { it.copy(pendingShare = null) }

    fun deleteOutput(output: DeliverableDto) {
        if (output.botId.isBlank() || output.name.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { api.deleteOutput(output.botId, output.name) }
                .onSuccess {
                    _state.update { s -> s.copy(busy = false, message = Notice(NoticeKey.OUTPUT_DELETED)) }
                    refresh()
                }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    fun clearBanner() = _state.update { it.copy(message = null, error = null) }

    private fun Throwable.readable(): Notice = toNotice()

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { DeliverablesViewModel(container.api) }
        }
    }
}
