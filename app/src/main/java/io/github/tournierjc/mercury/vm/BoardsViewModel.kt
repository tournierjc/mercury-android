package io.github.tournierjc.mercury.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.tournierjc.mercury.AppContainer
import io.github.tournierjc.mercury.data.AuthException
import io.github.tournierjc.mercury.data.BoardEvent
import io.github.tournierjc.mercury.data.BoardJson
import io.github.tournierjc.mercury.data.MercuryApi
import io.github.tournierjc.mercury.data.Notice
import io.github.tournierjc.mercury.data.NoticeKey
import io.github.tournierjc.mercury.data.toNotice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

data class CardDetail(
    val cardId: String,
    val comments: List<JsonObject> = emptyList(),
    val attachments: List<JsonObject> = emptyList(),
    val labels: List<JsonObject> = emptyList(),
    val dependencies: List<JsonObject> = emptyList(),
    val loading: Boolean = false,
)

/**
 * One attachment as the detail sheet shows it. A text file arrives through
 * `/content`; an image arrives as bytes from `/download` because that route is the only
 * one that reads a file without decoding it as UTF-8.
 */
data class AttachmentPreview(
    val attachmentId: String,
    val name: String,
    val mimeType: String,
    val isImage: Boolean,
    val isText: Boolean = false,
    val text: String? = null,
    /** The text is only the head of the file: the dialog says so, the words are not in the data. */
    val truncated: Boolean = false,
    val bytes: ByteArray? = null,
    val loading: Boolean = true,
    val error: Notice? = null,
)

/** Bytes ready for the system: the viewer for [download] = false, a copy for true. */
data class AttachmentFile(val name: String, val mimeType: String, val bytes: ByteArray)

data class AttachmentExport(val file: AttachmentFile, val download: Boolean)

data class BoardsUiState(
    val loading: Boolean = true,
    val boards: List<JsonObject> = emptyList(),
    /**
     * `false` when the boards service is not running on this instance. It is not the same screen as
     * an empty list: one says \"nothing to do\", the other says \"I cannot see your work\" — and the
     * second must never read as the first (SPECS/110 §5, Ticket C).
     */
    val available: Boolean? = null,
    val openBoardId: String? = null,
    val board: JsonObject? = null,
    val cards: List<JsonObject> = emptyList(),
    val plan: List<List<String>> = emptyList(),
    val feedback: List<JsonObject> = emptyList(),
    val context: JsonObject? = null,
    val cardDetail: CardDetail? = null,
    val attachment: AttachmentPreview? = null,
    val export: AttachmentExport? = null,
    /** True while `/api/boards/:id/events` is delivering frames. */
    val live: Boolean = false,
    val busy: Boolean = false,
    /** What the last finished action says (a key, resolved by the screen). */
    val message: Notice? = null,
    val error: Notice? = null,
) {
    val boardName: String get() = if (board != null) BoardJson.title(board) else ""
    val grouped: List<Pair<String, List<JsonObject>>> get() = BoardJson.byStatus(cards)
}

/**
 * Mercury's kanban execution surface: boards, cards, and the loop that runs them.
 * Every payload is navigated through [BoardJson] — no board exists on the reference
 * instance, so nothing here has an observed contract.
 */
class BoardsViewModel(
    private val api: MercuryApi,
    private val container: AppContainer,
) : ViewModel() {

    private val _state = MutableStateFlow(BoardsUiState())
    val state: StateFlow<BoardsUiState> = _state.asStateFlow()

    /** The event stream of the open board; null while no board is on screen. */
    private var liveJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { api.boards() }
                .onSuccess { root ->
                    _state.update {
                        it.copy(loading = false, boards = BoardJson.boards(root), available = BoardJson.available(root))
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.readable()) } }
            val openId = _state.value.openBoardId
            if (openId != null) loadBoard(openId)
        }
    }

    fun open(boardId: String) {
        _state.update { it.copy(openBoardId = boardId, cards = emptyList(), board = null, plan = emptyList()) }
        loadBoard(boardId)
    }

    fun close() {
        stopLive()
        _state.update {
            it.copy(
                openBoardId = null,
                board = null,
                cards = emptyList(),
                cardDetail = null,
                attachment = null,
                live = false,
            )
        }
    }

    // ------------------------------------------------------------------
    // Live board: the SSE feed the board screen holds open
    // ------------------------------------------------------------------

    /**
     * Subscribes to `/api/boards/:id/events` and applies every frame until [stopLive].
     *
     * Each `board-update` frame carries the whole board, so a running card's status,
     * progress and token count move without the user doing anything. When the stream ends
     * — a proxy that buffers SSE, a restart of the daemon — the coroutine polls the board
     * instead and retries with a growing backoff, so a broken stream costs latency, never
     * freshness, and the screen keeps working on a server that has no events route.
     */
    fun startLive(boardId: String) {
        if (liveJob?.isActive == true) return
        liveJob = viewModelScope.launch {
            var backoff = RECONNECT_MIN_MS
            var everLive = false
            while (isActive) {
                var frames = 0
                try {
                    api.boardEvents(boardId).collect { event ->
                        frames++
                        everLive = true
                        backoff = RECONNECT_MIN_MS
                        applyLive(event)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A refusal before the first frame is worth saying; a stream that drops
                    // after working is covered by the poll below, and the header says so.
                    if (frames == 0 && !everLive && e !is AuthException) {
                        _state.update { it.copy(error = e.readable()) }
                    }
                }
                if (!isActive) break
                _state.update { it.copy(live = false) }
                pollWhileDown(boardId, POLL_WINDOW_MS)
                if (!isActive) break
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(RECONNECT_MAX_MS)
            }
        }
    }

    fun stopLive() {
        liveJob?.cancel()
        liveJob = null
        if (_state.value.live) _state.update { it.copy(live = false) }
    }

    private fun applyLive(event: BoardEvent) {
        when (event) {
            is BoardEvent.Update -> _state.update { current ->
                val detail = current.cardDetail
                val card = detail?.let { open -> event.cards.firstOrNull { BoardJson.id(it) == open.cardId } }
                current.copy(
                    board = event.board,
                    cards = event.cards,
                    // Labels and dependencies live on the card, so the open sheet follows
                    // the frame without another round-trip.
                    cardDetail = if (detail != null && card != null) {
                        detail.copy(
                            labels = BoardJson.labels(card),
                            dependencies = BoardJson.dependencies(card),
                        )
                    } else {
                        detail
                    },
                    live = true,
                    error = null,
                )
            }

            is BoardEvent.FeedbackRequest -> _state.update { current ->
                val id = BoardJson.id(event.request)
                val merged = if (id.isBlank()) {
                    listOf(event.request) + current.feedback
                } else {
                    listOf(event.request) + current.feedback.filter { BoardJson.id(it) != id }
                }
                // A card is blocked on the owner: say it in the banner, since the board screen
                // has no other place to speak.
                val question = BoardJson.feedbackQuestion(event.request)
                current.copy(
                    feedback = merged,
                    message = question.ifBlank { null }?.let { Notice(NoticeKey.BOARD_DECISION_REQUESTED, args = listOf(it)) },
                    live = true,
                )
            }

            is BoardEvent.Unknown -> _state.update { it.copy(live = true) }
        }
    }

    /** Fallback while the stream is down: refetch the board a few times before retrying. */
    private suspend fun pollWhileDown(boardId: String, windowMs: Long) {
        val deadline = System.currentTimeMillis() + windowMs
        while (System.currentTimeMillis() < deadline) {
            delay(POLL_MS)
            runCatching { api.board(boardId) }
                .onSuccess { root ->
                    _state.update { it.copy(board = root, cards = BoardJson.cards(root)) }
                }
        }
    }

    fun createBoard(name: String, description: String?) {
        if (name.isBlank()) return
        act(Notice(NoticeKey.BOARD_CREATED)) { api.createBoard(name.trim(), description?.trim()?.ifBlank { null }) }
    }

    fun deleteBoard(id: String) = act(Notice(NoticeKey.BOARD_DELETED)) { api.deleteBoard(id) }

    fun activate() = actOnBoard(Notice(NoticeKey.BOARD_ACTIVATED)) { api.activateBoard(it) }

    /** The open board's switch, as the toolbar draws it. */
    fun setActive(active: Boolean) =
        actOnBoard(Notice(if (active) NoticeKey.BOARD_ACTIVATED else NoticeKey.BOARD_DEACTIVATED)) {
            if (active) api.activateBoard(it) else api.deactivateBoard(it)
        }

    /**
     * A board's switch in the list, where the board is *not* open: the web app's Active pill works
     * from the list, and the phone's list said `actif` without offering the tap.
     */
    fun setBoardActive(boardId: String, active: Boolean) =
        act(Notice(if (active) NoticeKey.BOARD_ACTIVATED else NoticeKey.BOARD_DEACTIVATED)) {
            if (active) api.activateBoard(boardId) else api.deactivateBoard(boardId)
        }

    fun deactivate() = actOnBoard(Notice(NoticeKey.BOARD_DEACTIVATED)) { api.deactivateBoard(it) }

    fun addCard(task: String, priority: String?) {
        if (task.isBlank()) return
        actOnBoard(Notice(NoticeKey.CARD_ADDED)) { api.addCard(it, task.trim(), priority?.ifBlank { null }) }
    }

    fun deleteCard(cardId: String) = actOnBoard(Notice(NoticeKey.CARD_DELETED)) { api.deleteCard(it, cardId) }

    fun runCard(cardId: String) = actOnBoard(Notice(NoticeKey.CARD_STARTED)) { api.runCard(it, cardId) }

    fun haltCard(cardId: String) = actOnBoard(Notice(NoticeKey.CARD_HALTED)) { api.haltCard(it, cardId) }

    fun cascadeCard(cardId: String) = actOnBoard(Notice(NoticeKey.CASCADE_REQUESTED)) { api.cascadeCard(it, cardId) }

    fun runAll() = actOnBoard(Notice(NoticeKey.CARDS_ALL_STARTED)) { api.runAllCards(it) }

    fun haltAll() = actOnBoard(Notice(NoticeKey.CARDS_ALL_HALTED)) { api.haltAllCards(it) }

    fun smartExecute() = actOnBoard(Notice(NoticeKey.SMART_RUN_REQUESTED)) { api.smartExecute(it) }

    fun clearDone() = actOnBoard(Notice(NoticeKey.DONE_CARDS_CLEARED)) { api.clearDoneCards(it) }

    fun generate(prompt: String?) = actOnBoard(Notice(NoticeKey.GENERATION_REQUESTED)) { api.generateCards(it, prompt?.ifBlank { null }) }

    fun loadPlan() {
        val boardId = _state.value.openBoardId ?: return
        viewModelScope.launch {
            runCatching { api.executionPlan(boardId) }
                .onSuccess { root -> _state.update { it.copy(plan = BoardJson.batchLabels(root)) } }
                .onFailure { e -> _state.update { it.copy(error = e.readable()) } }
        }
    }

    fun loadContext() {
        val boardId = _state.value.openBoardId ?: return
        viewModelScope.launch {
            runCatching { api.boardContext(boardId) }
                .onSuccess { ctx -> _state.update { it.copy(context = ctx) } }
                .onFailure { e -> _state.update { it.copy(error = e.readable()) } }
        }
    }

    fun setInstructions(text: String) = actOnBoard(Notice(NoticeKey.INSTRUCTIONS_SAVED)) { api.setContextInstructions(it, text) }

    fun setDirectory(text: String) = actOnBoard(Notice(NoticeKey.DIRECTORY_SAVED)) { api.setContextDirectory(it, text) }

    fun setKnowledge(text: String) = actOnBoard(Notice(NoticeKey.KNOWLEDGE_SAVED)) { api.setContextKnowledge(it, text) }

    fun setVariable(key: String, value: String) =
        actOnBoard(Notice(NoticeKey.VARIABLE_SAVED)) { api.setContextVariable(it, key, value) }

    fun loadFeedback() {
        val boardId = _state.value.openBoardId ?: return
        viewModelScope.launch {
            runCatching { api.boardFeedback(boardId) }
                .onSuccess { root -> _state.update { it.copy(feedback = BoardJson.feedback(root)) } }
                .onFailure { e -> _state.update { it.copy(error = e.readable()) } }
        }
    }

    fun respondFeedback(feedbackId: String, response: String) = actOnBoard(Notice(NoticeKey.FEEDBACK_SENT)) {
        api.respondFeedback(it, feedbackId, response)
    }

    // Card detail: comments, attachments, labels, dependencies.

    fun openCard(cardId: String) {
        val boardId = _state.value.openBoardId ?: return
        _state.update { it.copy(cardDetail = CardDetail(cardId = cardId, loading = true), attachment = null) }
        viewModelScope.launch {
            val comments = runCatching { api.cardComments(boardId, cardId) }.getOrNull()
            val attachments = runCatching { api.cardAttachments(boardId, cardId) }.getOrNull()
            val card = _state.value.cards.firstOrNull { BoardJson.id(it) == cardId }
            _state.update {
                it.copy(
                    cardDetail = CardDetail(
                        cardId = cardId,
                        comments = BoardJson.comments(comments),
                        attachments = BoardJson.attachments(attachments),
                        labels = BoardJson.labels(card),
                        dependencies = BoardJson.dependencies(card),
                        loading = false,
                    ),
                )
            }
        }
    }

    fun closeCard() {
        _state.update { it.copy(cardDetail = null, attachment = null) }
    }

    // ------------------------------------------------------------------
    // Attachments: reading what was uploaded
    // ------------------------------------------------------------------

    /**
     * Reads one attachment. A text deliverable comes from `/content`, which is the only
     * route that hands the file over readable; an image comes from `/download` as bytes,
     * since reading a PNG as UTF-8 would produce noise. Anything else (a PDF, a deck) has
     * nothing to draw here and goes to the viewer as it is.
     */
    fun openAttachment(attachment: JsonObject) {
        val boardId = _state.value.openBoardId ?: return
        val cardId = _state.value.cardDetail?.cardId ?: return
        val attachmentId = BoardJson.id(attachment)
        if (attachmentId.isBlank()) return

        val preview = AttachmentPreview(
            attachmentId = attachmentId,
            name = BoardJson.title(attachment),
            mimeType = BoardJson.attachmentMimeType(attachment),
            isImage = BoardJson.attachmentIsImage(attachment),
            isText = BoardJson.attachmentIsText(attachment),
        )
        _state.update { it.copy(attachment = preview) }

        viewModelScope.launch {
            val loaded = when {
                preview.isImage -> runCatching {
                    api.cardAttachmentDownload(boardId, cardId, attachmentId)
                }.fold(
                    onSuccess = { file ->
                        preview.copy(
                            bytes = file.bytes,
                            mimeType = file.mimeType,
                            name = file.fileName.ifBlank { preview.name },
                        )
                    },
                    onFailure = { e -> preview.copy(error = e.readable()) },
                )

                preview.isText -> runCatching {
                    api.cardAttachmentContent(boardId, cardId, attachmentId)
                }.fold(
                    onSuccess = { root ->
                        val content = BoardJson.attachmentContent(root)
                        preview.copy(
                            text = content.take(MAX_PREVIEW_CHARS),
                            truncated = content.length > MAX_PREVIEW_CHARS,
                        )
                    },
                    onFailure = { e -> preview.copy(error = e.readable()) },
                )

                else -> preview
            }
            _state.update { current ->
                // The sheet may have been closed — or another attachment opened — while the
                // bytes were in flight: a late answer never reopens it.
                if (current.attachment?.attachmentId != preview.attachmentId) {
                    current
                } else {
                    current.copy(attachment = loaded.copy(loading = false))
                }
            }
        }
    }

    fun closeAttachment() {
        _state.update { it.copy(attachment = null) }
    }

    /**
     * Asks for the file to leave the app: [download] = true saves a copy through the
     * system picker, false opens it in whatever app reads that type. The bytes come from
     * `/download`, cached when the image preview already fetched them.
     */
    fun exportAttachment(download: Boolean) {
        val preview = _state.value.attachment ?: return
        preview.bytes?.let { bytes ->
            _state.update {
                it.copy(export = AttachmentExport(AttachmentFile(preview.name, preview.mimeType, bytes), download))
            }
            return
        }
        val boardId = _state.value.openBoardId ?: return
        val cardId = _state.value.cardDetail?.cardId ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            runCatching { api.cardAttachmentDownload(boardId, cardId, preview.attachmentId) }
                .onSuccess { file ->
                    _state.update {
                        it.copy(
                            busy = false,
                            export = AttachmentExport(
                                AttachmentFile(
                                    name = file.fileName.ifBlank { preview.name },
                                    mimeType = file.mimeType.ifBlank { preview.mimeType },
                                    bytes = file.bytes,
                                ),
                                download,
                            ),
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.readable()) } }
        }
    }

    fun consumeExport() {
        _state.update { it.copy(export = null) }
    }

    /** The UI explains a system refusal it alone can see (no app opens this type, no saver). */
    fun report(notice: Notice) {
        _state.update { it.copy(error = notice) }
    }

    fun addComment(text: String) {
        val boardId = _state.value.openBoardId ?: return
        val cardId = _state.value.cardDetail?.cardId ?: return
        act(Notice(NoticeKey.COMMENT_ADDED)) { api.addComment(boardId, cardId, text) }
        openCard(cardId)
    }

    fun deleteComment(commentId: String) {
        val boardId = _state.value.openBoardId ?: return
        val cardId = _state.value.cardDetail?.cardId ?: return
        act(Notice(NoticeKey.COMMENT_DELETED)) { api.deleteComment(boardId, cardId, commentId) }
        openCard(cardId)
    }

    fun addAttachment(name: String, path: String) {
        val boardId = _state.value.openBoardId ?: return
        val cardId = _state.value.cardDetail?.cardId ?: return
        act(Notice(NoticeKey.BOARD_ATTACHMENT_ADDED)) { api.addAttachment(boardId, cardId, name, path) }
        openCard(cardId)
    }

    fun deleteAttachment(attachmentId: String) {
        val boardId = _state.value.openBoardId ?: return
        val cardId = _state.value.cardDetail?.cardId ?: return
        act(Notice(NoticeKey.BOARD_ATTACHMENT_REMOVED)) { api.deleteAttachment(boardId, cardId, attachmentId) }
        closeAttachment()
        openCard(cardId)
    }

    fun addLabel(name: String, color: String) {
        val boardId = _state.value.openBoardId ?: return
        val cardId = _state.value.cardDetail?.cardId ?: return
        act(Notice(NoticeKey.CARD_LABEL_ADDED)) { api.addLabel(boardId, cardId, name, color) }
        openCard(cardId)
    }

    fun removeLabel(labelId: String) {
        val boardId = _state.value.openBoardId ?: return
        val cardId = _state.value.cardDetail?.cardId ?: return
        act(Notice(NoticeKey.CARD_LABEL_REMOVED)) { api.removeLabel(boardId, cardId, labelId) }
        openCard(cardId)
    }

    fun addDependency(dependsOnCardId: String) {
        val boardId = _state.value.openBoardId ?: return
        val cardId = _state.value.cardDetail?.cardId ?: return
        act(Notice(NoticeKey.DEPENDENCY_ADDED)) { api.addDependency(boardId, cardId, dependsOnCardId) }
        openCard(cardId)
    }

    fun removeDependency(dependencyId: String) {
        val boardId = _state.value.openBoardId ?: return
        val cardId = _state.value.cardDetail?.cardId ?: return
        act(Notice(NoticeKey.DEPENDENCY_REMOVED)) { api.removeDependency(boardId, cardId, dependencyId) }
        openCard(cardId)
    }

    fun setParent(parentId: String?) {
        val boardId = _state.value.openBoardId ?: return
        val cardId = _state.value.cardDetail?.cardId ?: return
        act(Notice(NoticeKey.CARD_PARENT_SET)) { api.setCardParent(boardId, cardId, parentId?.ifBlank { null }) }
        openCard(cardId)
    }

    fun clearBanner() {
        _state.update { it.copy(message = null, error = null) }
    }

    private fun loadBoard(boardId: String) {
        viewModelScope.launch {
            runCatching { api.board(boardId) }
                .onSuccess { root ->
                    _state.update { it.copy(board = root, cards = BoardJson.cards(root), error = null) }
                }
                .onFailure { e -> _state.update { it.copy(error = e.readable()) } }
        }
    }

    private fun actOnBoard(success: Notice, block: suspend (String) -> Any?) {
        val boardId = _state.value.openBoardId ?: return
        act(success) { block(boardId) }
    }

    private fun act(success: Notice, block: suspend () -> Any?) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null, error = null) }
            runCatching { block() }
                .onSuccess {
                    _state.update { s -> s.copy(busy = false, message = success) }
                    refresh()
                }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    /** One mapping for the whole app (see `toNotice`). */
    private fun Throwable.readable(): Notice = toNotice()

    companion object {
        /** One poll each while the stream is down: fast enough to see a card move. */
        private const val POLL_MS = 8_000L
        private const val POLL_WINDOW_MS = 16_000L
        private const val RECONNECT_MIN_MS = 2_000L
        private const val RECONNECT_MAX_MS = 60_000L

        /** A markdown deliverable can be long; the sheet shows the head, the file the rest. */
        private const val MAX_PREVIEW_CHARS = 40_000

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { BoardsViewModel(container.api, container) }
        }
    }
}
