package io.github.tournierjc.mercury.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.tournierjc.mercury.AppContainer
import io.github.tournierjc.mercury.data.AuthException
import io.github.tournierjc.mercury.data.BoardJson
import io.github.tournierjc.mercury.data.MercuryApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

data class BoardsUiState(
    val loading: Boolean = true,
    val boards: List<JsonObject> = emptyList(),
    val openBoardId: String? = null,
    val board: JsonObject? = null,
    val cards: List<JsonObject> = emptyList(),
    val plan: List<List<String>> = emptyList(),
    val feedback: List<JsonObject> = emptyList(),
    val context: JsonObject? = null,
    val cardDetail: CardDetail? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
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

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { api.boards() }
                .onSuccess { root -> _state.update { it.copy(loading = false, boards = BoardJson.boards(root)) } }
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
        _state.update { it.copy(openBoardId = null, board = null, cards = emptyList(), cardDetail = null) }
    }

    fun createBoard(name: String, description: String?) {
        if (name.isBlank()) return
        act("Plateau créé") { api.createBoard(name.trim(), description?.trim()?.ifBlank { null }) }
    }

    fun deleteBoard(id: String) = act("Plateau supprimé") { api.deleteBoard(id) }

    fun activate() = actOnBoard("Plateau activé") { api.activateBoard(it) }

    fun deactivate() = actOnBoard("Plateau désactivé") { api.deactivateBoard(it) }

    fun addCard(task: String, priority: String?) {
        if (task.isBlank()) return
        actOnBoard("Carte ajoutée") { api.addCard(it, task.trim(), priority?.ifBlank { null }) }
    }

    fun deleteCard(cardId: String) = actOnBoard("Carte supprimée") { api.deleteCard(it, cardId) }

    fun runCard(cardId: String) = actOnBoard("Carte lancée") { api.runCard(it, cardId) }

    fun haltCard(cardId: String) = actOnBoard("Carte arrêtée") { api.haltCard(it, cardId) }

    fun cascadeCard(cardId: String) = actOnBoard("Cascade demandée") { api.cascadeCard(it, cardId) }

    fun runAll() = actOnBoard("Exécution de toutes les cartes demandée") { api.runAllCards(it) }

    fun haltAll() = actOnBoard("Exécution interrompue") { api.haltAllCards(it) }

    fun smartExecute() = actOnBoard("Exécution intelligente demandée") { api.smartExecute(it) }

    fun clearDone() = actOnBoard("Cartes terminées effacées") { api.clearDoneCards(it) }

    fun generate(prompt: String?) = actOnBoard("Génération demandée") { api.generateCards(it, prompt?.ifBlank { null }) }

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

    fun setInstructions(text: String) = actOnBoard("Consignes enregistrées") { api.setContextInstructions(it, text) }

    fun setDirectory(text: String) = actOnBoard("Dossier enregistré") { api.setContextDirectory(it, text) }

    fun setKnowledge(text: String) = actOnBoard("Connaissances enregistrées") { api.setContextKnowledge(it, text) }

    fun setVariable(key: String, value: String) =
        actOnBoard("Variable enregistrée") { api.setContextVariable(it, key, value) }

    fun loadFeedback() {
        val boardId = _state.value.openBoardId ?: return
        viewModelScope.launch {
            runCatching { api.boardFeedback(boardId) }
                .onSuccess { root -> _state.update { it.copy(feedback = BoardJson.feedback(root)) } }
                .onFailure { e -> _state.update { it.copy(error = e.readable()) } }
        }
    }

    fun respondFeedback(feedbackId: String, response: String) = actOnBoard("Réponse envoyée") {
        api.respondFeedback(it, feedbackId, response)
    }

    // Card detail: comments, attachments, labels, dependencies.

    fun openCard(cardId: String) {
        val boardId = _state.value.openBoardId ?: return
        _state.update { it.copy(cardDetail = CardDetail(cardId = cardId, loading = true)) }
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
        _state.update { it.copy(cardDetail = null) }
    }

    fun addComment(text: String) {
        val boardId = _state.value.openBoardId ?: return
        val cardId = _state.value.cardDetail?.cardId ?: return
        act("Commentaire ajouté") { api.addComment(boardId, cardId, text) }
        openCard(cardId)
    }

    fun deleteComment(commentId: String) {
        val boardId = _state.value.openBoardId ?: return
        val cardId = _state.value.cardDetail?.cardId ?: return
        act("Commentaire supprimé") { api.deleteComment(boardId, cardId, commentId) }
        openCard(cardId)
    }

    fun addAttachment(name: String, path: String) {
        val boardId = _state.value.openBoardId ?: return
        val cardId = _state.value.cardDetail?.cardId ?: return
        act("Pièce jointe ajoutée") { api.addAttachment(boardId, cardId, name, path) }
        openCard(cardId)
    }

    fun deleteAttachment(attachmentId: String) {
        val boardId = _state.value.openBoardId ?: return
        val cardId = _state.value.cardDetail?.cardId ?: return
        act("Pièce jointe retirée") { api.deleteAttachment(boardId, cardId, attachmentId) }
        openCard(cardId)
    }

    fun addLabel(name: String, color: String) {
        val boardId = _state.value.openBoardId ?: return
        val cardId = _state.value.cardDetail?.cardId ?: return
        act("Étiquette ajoutée") { api.addLabel(boardId, cardId, name, color) }
        openCard(cardId)
    }

    fun removeLabel(labelId: String) {
        val boardId = _state.value.openBoardId ?: return
        val cardId = _state.value.cardDetail?.cardId ?: return
        act("Étiquette retirée") { api.removeLabel(boardId, cardId, labelId) }
        openCard(cardId)
    }

    fun addDependency(dependsOnCardId: String) {
        val boardId = _state.value.openBoardId ?: return
        val cardId = _state.value.cardDetail?.cardId ?: return
        act("Dépendance ajoutée") { api.addDependency(boardId, cardId, dependsOnCardId) }
        openCard(cardId)
    }

    fun removeDependency(dependencyId: String) {
        val boardId = _state.value.openBoardId ?: return
        val cardId = _state.value.cardDetail?.cardId ?: return
        act("Dépendance retirée") { api.removeDependency(boardId, cardId, dependencyId) }
        openCard(cardId)
    }

    fun setParent(parentId: String?) {
        val boardId = _state.value.openBoardId ?: return
        val cardId = _state.value.cardDetail?.cardId ?: return
        act("Parent défini") { api.setCardParent(boardId, cardId, parentId?.ifBlank { null }) }
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

    private fun actOnBoard(successMessage: String, block: suspend (String) -> Any?) {
        val boardId = _state.value.openBoardId ?: return
        act(successMessage) { block(boardId) }
    }

    private fun act(successMessage: String, block: suspend () -> Any?) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null, error = null) }
            runCatching { block() }
                .onSuccess {
                    _state.update { s -> s.copy(busy = false, message = successMessage) }
                    refresh()
                }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    private fun Throwable.readable(): String = when (this) {
        is AuthException -> "Session expirée — reconnecte-toi"
        else -> message ?: "Requête refusée"
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { BoardsViewModel(container.api, container) }
        }
    }
}
