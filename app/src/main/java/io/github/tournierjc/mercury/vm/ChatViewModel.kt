package io.github.tournierjc.mercury.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.tournierjc.mercury.AppContainer
import io.github.tournierjc.mercury.data.Attachments
import io.github.tournierjc.mercury.data.AuthException
import io.github.tournierjc.mercury.data.ChatMessage
import io.github.tournierjc.mercury.data.ChatReducer
import io.github.tournierjc.mercury.data.Notice
import io.github.tournierjc.mercury.data.NoticeKey
import io.github.tournierjc.mercury.data.toNotice
import io.github.tournierjc.mercury.data.ChatState
import io.github.tournierjc.mercury.data.CodeMode
import io.github.tournierjc.mercury.data.MercuryApi
import io.github.tournierjc.mercury.data.PendingFile
import io.github.tournierjc.mercury.data.ThreadDto
import io.github.tournierjc.mercury.data.ToolTrace
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import io.github.tournierjc.mercury.ui.attachmentNotice

class ChatViewModel(
    private val api: MercuryApi,
    private val container: AppContainer,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState(serverUrl = container.serverUrl))
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private val _threads = MutableStateFlow<List<ThreadDto>>(emptyList())
    val threads: StateFlow<List<ThreadDto>> = _threads.asStateFlow()

    private val _signedIn = MutableStateFlow(api.hasSession())
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /**
     * True until the first round-trip of a session lands. The screen draws a moving line for it: the
     * app used to jump from an empty transcript to a full one with nothing in between, which reads
     * exactly like "your threads are gone" for as long as the network takes.
     */
    private val _syncing = MutableStateFlow(api.hasSession())
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private var eventsJob: Job? = null
    private var streamSession: String? = null

    /**
     * The programming mode, as the daemon reports it. Held here because the choice now lives in the
     * conversation: the console used to own it, on a screen about disk and plugins.
     */
    private val _codeMode = MutableStateFlow(CodeMode.Off)
    val codeMode: StateFlow<CodeMode> = _codeMode.asStateFlow()

    /** False when this runtime has no programming mode at all: the control then draws nothing. */
    private val _codeModeAvailable = MutableStateFlow(false)
    val codeModeAvailable: StateFlow<Boolean> = _codeModeAvailable.asStateFlow()

    init {
        if (api.hasSession()) {
            _state.update { it.copy(connected = true) }
            refreshThreads()
            refreshCodeMode()
        }
    }

    fun signIn(rawUrl: String, username: String, password: String) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                api.login(rawUrl, username, password)
                val stored = MercuryApi.normalizeBaseUrl(rawUrl)
                container.serverUrl = stored
                container.lastUsername = username
                api.restoreBaseUrl(stored)
                _state.update { it.copy(serverUrl = stored, connected = true, error = null) }
                _signedIn.value = true
                ensureEvents(_state.value.sessionId)
                refreshThreads()
            } catch (e: AuthException) {
                _state.update { ChatReducer.setError(it, e.toNotice()) }
            } catch (e: Exception) {
                _state.update { ChatReducer.setError(it, Notice(NoticeKey.CONNECT_FAILED).withDetail(e.message)) }
            } finally {
                _busy.value = false
            }
        }
    }

    fun signOut() {
        eventsJob?.cancel()
        eventsJob = null
        streamSession = null
        api.clearSession()
        _signedIn.value = false
        _threads.value = emptyList()
        _state.value = ChatState(serverUrl = _state.value.serverUrl)
    }

    /**
     * Points the app at another Mercury host.
     *
     * The stored address, the HTTP client's base URL and the session cookie all belong to the old
     * server, so they move together: keeping the cookie would leave the app signed in to a host it
     * no longer talks to, and the next request would arrive as a stranger. Changing one without the
     * other two is why this is a single method and not three calls from the dialog.
     */
    fun changeServer(rawUrl: String) {
        val normalized = MercuryApi.normalizeBaseUrl(rawUrl)
        if (normalized.isBlank()) return
        eventsJob?.cancel()
        eventsJob = null
        streamSession = null
        api.clearSession()
        container.serverUrl = normalized
        api.restoreBaseUrl(normalized)
        _threads.value = emptyList()
        _signedIn.value = false
        _state.value = ChatState(serverUrl = normalized)
    }

    fun refreshThreads() {
        viewModelScope.launch {
            runCatching { api.threads() }
                .onSuccess { list -> _threads.value = list }
                .onFailure { error ->
                    if (error is AuthException) {
                        _signedIn.value = false
                    }
                }
            // Arrived or failed, the startup sync is over: a line that never stops is a lie.
            _syncing.value = false
        }
    }

    private val _attachments = MutableStateFlow<List<PendingFile>>(emptyList())
    val attachments: StateFlow<List<PendingFile>> = _attachments.asStateFlow()

    /**
     * Queues picked files. The caps are checked here so the user hears about a
     * 20 MB PDF now rather than after a failed send.
     */
    fun attach(files: List<PendingFile>) {
        val next = (_attachments.value + files).take(Attachments.MAX_FILES)
        val notice = attachmentNotice(Attachments.problemWith(next))
        if (notice != null) {
            _state.update { ChatReducer.setError(it, notice) }
            return
        }
        _attachments.value = next
    }

    fun removeAttachment(index: Int) {
        _attachments.value = _attachments.value.filterIndexed { position, _ -> position != index }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        val files = _attachments.value
        if ((trimmed.isEmpty() && files.isEmpty()) || _state.value.streaming) return
        // A photo with no caption still shows as a turn: name what travels rather than count it,
        // so the bubble needs no sentence (and no translation).
        val shown = trimmed.ifBlank { files.joinToString(" · ") { it.name } }
        _state.update { ChatReducer.addUserMessage(it, shown) }
        viewModelScope.launch {
            try {
                val sessionId = api.send(trimmed, _state.value.sessionId, files.map { it.toUpload() })
                if (sessionId != null) {
                    _attachments.value = emptyList()
                    _state.update { it.copy(sessionId = sessionId) }
                    ensureEvents(sessionId)
                    // The drawer used to stay empty until the next launch: a send that created
                    // a session is exactly when the list changes.
                    refreshThreads()
                }
            } catch (e: AuthException) {
                _signedIn.value = false
                _state.update { ChatReducer.setError(it, e.toNotice()) }
            } catch (e: Exception) {
                // "Envoi impossible" alone told the owner nothing and hid a null message.
                // Name the failure instead of guessing: the class, and the message if any.
                val reason = e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName
                _state.update { ChatReducer.setError(it, Notice(NoticeKey.SEND_FAILED).withDetail(reason)) }
            }
        }
    }

    fun answerPermission(id: String, action: String) {
        _state.update { ChatReducer.clearPermission(it) }
        viewModelScope.launch {
            runCatching { api.resolvePermission(id, action) }
                .onFailure { e ->
                    _state.update { ChatReducer.setError(it, Notice(NoticeKey.REPLY_REFUSED).withDetail(e.message)) }
                }
        }
    }

    fun newChat() {
        eventsJob?.cancel()
        eventsJob = null
        streamSession = null
        _state.value = ChatReducer.newChat(_state.value)
        ensureEvents(null)
    }

    /** Reads the mode the daemon is in; silent when the route is absent, as older runtimes are. */
    fun refreshCodeMode() {
        viewModelScope.launch {
            runCatching { api.codeStatus() }
                .onSuccess { status ->
                    _codeModeAvailable.value = status.available
                    _codeMode.value = CodeMode.from(status.state)
                }
        }
    }

    /** Chooses a mode. The screen redraws from the daemon's answer, not from the tap. */
    fun setCodeMode(mode: CodeMode) {
        viewModelScope.launch {
            runCatching { api.setCodeMode(mode.wire) }
                .onSuccess { status ->
                    _codeModeAvailable.value = status.available
                    _codeMode.value = CodeMode.from(status.state)
                }
                .onFailure { e ->
                    _state.update { ChatReducer.setError(it, Notice(NoticeKey.CODE_MODE_FAILED).withDetail(e.message)) }
                }
        }
    }

    fun openThread(thread: ThreadDto) {
        eventsJob?.cancel()
        eventsJob = null
        streamSession = null
        _state.update {
            it.copy(sessionId = thread.id, title = thread.title.ifBlank { thread.alias }, messages = emptyList())
        }
        viewModelScope.launch {
            runCatching { api.threadMessages(thread.id) }
                .onSuccess { messages ->
                    _state.update { current ->
                        current.copy(messages = messages.mapIndexed { index, dto ->
                            // A turn that used tools leaves a summary written for the model. Shown
                            // verbatim it was a wall of `- tool → ok`; parsed, it is the same chips
                            // the live turn had.
                            val trace = dto.kind == "tool-call" || ToolTrace.looksLike(dto.content)
                            ChatMessage(
                                id = "history-$index",
                                role = if (dto.role == "user") ChatMessage.Role.User else ChatMessage.Role.Assistant,
                                text = if (trace) "" else dto.content,
                                steps = if (trace) ToolTrace.parse(dto.content) else emptyList(),
                                toolTrace = trace,
                            )
                        })
                    }
                    ensureEvents(thread.id)
                }
                .onFailure { e ->
                    _state.update { ChatReducer.setError(it, Notice(NoticeKey.HISTORY_UNAVAILABLE).withDetail(e.message)) }
                }
        }
    }

    /** Deletes a thread server-side, then drops it from the drawer. */
    fun deleteThread(thread: ThreadDto) {
        viewModelScope.launch {
            runCatching { api.deleteThread(thread.id) }
                .onSuccess {
                    _threads.value = _threads.value.filterNot { it.id == thread.id }
                    if (_state.value.sessionId == thread.id) newChat()
                }
                .onFailure { e ->
                    _state.update { ChatReducer.setError(it, Notice(NoticeKey.DELETE_FAILED).withDetail(e.message)) }
                }
        }
    }

    fun dismissWarning() {
        _state.update { ChatReducer.dismissWarning(it) }
    }

    fun dismissError() {
        _state.update { ChatReducer.setError(it, null) }
    }

    private fun ensureEvents(sessionId: String?) {
        if (eventsJob != null && streamSession == sessionId) return
        eventsJob?.cancel()
        streamSession = sessionId
        eventsJob = viewModelScope.launch {
            api.events(sessionId).collect { event ->
                _state.update { ChatReducer.onEvent(it, event) }
            }
        }
    }

    override fun onCleared() {
        eventsJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ChatViewModel(container.api, container) }
        }
    }
}
