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
import io.github.tournierjc.mercury.data.ChatState
import io.github.tournierjc.mercury.data.MercuryApi
import io.github.tournierjc.mercury.data.PendingFile
import io.github.tournierjc.mercury.data.ThreadDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    private var eventsJob: Job? = null
    private var streamSession: String? = null

    init {
        if (api.hasSession()) {
            _state.update { it.copy(connected = true) }
            refreshThreads()
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
                _state.update { ChatReducer.setError(it, e.message) }
            } catch (e: Exception) {
                _state.update { ChatReducer.setError(it, e.message ?: "Connexion impossible") }
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

    fun refreshThreads() {
        viewModelScope.launch {
            runCatching { api.threads() }
                .onSuccess { list -> _threads.value = list }
                .onFailure { error ->
                    if (error is AuthException) {
                        _signedIn.value = false
                    }
                }
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
        val problem = Attachments.problemWith(next) ?: if (files.size + _attachments.value.size > Attachments.MAX_FILES) {
            "4 pièces jointes au maximum"
        } else {
            null
        }
        if (problem != null) {
            _state.update { ChatReducer.setError(it, problem) }
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
        // A photo with no caption still shows as a turn: say what travels.
        val shown = trimmed.ifBlank { "(" + files.size + " pièce(s) jointe(s))" }
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
                _state.update { ChatReducer.setError(it, e.message) }
            } catch (e: Exception) {
                // "Envoi impossible" alone told the owner nothing and hid a null message.
                // Name the failure instead of guessing: the class, and the message if any.
                val reason = e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName ?: "erreur"
                _state.update { ChatReducer.setError(it, "Envoi impossible — $reason") }
            }
        }
    }

    fun answerPermission(id: String, action: String) {
        _state.update { ChatReducer.clearPermission(it) }
        viewModelScope.launch {
            runCatching { api.resolvePermission(id, action) }
                .onFailure { e ->
                    _state.update { ChatReducer.setError(it, e.message ?: "Réponse refusée") }
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
                            ChatMessage(
                                id = "history-$index",
                                role = if (dto.role == "user") ChatMessage.Role.User else ChatMessage.Role.Assistant,
                                text = dto.content,
                            )
                        })
                    }
                    ensureEvents(thread.id)
                }
                .onFailure { e ->
                    _state.update { ChatReducer.setError(it, e.message ?: "Historique indisponible") }
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
                    _state.update { ChatReducer.setError(it, e.message ?: "Suppression impossible") }
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
