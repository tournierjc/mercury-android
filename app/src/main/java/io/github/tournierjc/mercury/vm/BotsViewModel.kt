package io.github.tournierjc.mercury.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.tournierjc.mercury.AppContainer
import io.github.tournierjc.mercury.data.AuthException
import io.github.tournierjc.mercury.data.BotJson
import io.github.tournierjc.mercury.data.MercuryApi
import io.github.tournierjc.mercury.data.dto.BotCrewResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class BotsUiState(
    val selectedId: String? = null,
    val manifest: JsonObject? = null,
    val liveState: JsonObject? = null,
    val persona: String = "",
    val personaLoaded: Boolean = false,
    val permissions: JsonObject? = null,
    val crew: BotCrewResponse? = null,
    val tasks: List<JsonObject> = emptyList(),
    val journal: List<JsonObject> = emptyList(),
    val inbox: List<JsonObject> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val dispatchFeedback: String? = null,
    val detailLoading: Boolean = false,
)

/**
 * Teammates: customization (persona, permissions, model, autonomy), supervision (activity,
 * needs-you, journal), the fleet (crew, delegated tasks) and background dispatch.
 */
class BotsViewModel(
    private val api: MercuryApi,
    private val container: AppContainer,
) : ViewModel() {

    val watcher = container.botWatcher

    private val _state = MutableStateFlow(BotsUiState())
    val state: StateFlow<BotsUiState> = _state.asStateFlow()

    init {
        watcher.start()
    }

    fun refreshRoster() {
        viewModelScope.launch {
            runCatching { api.bots() }
                .onSuccess { _state.update { s -> s.copy(error = null) } }
                .onFailure { e -> _state.update { s -> s.copy(error = e.readable()) } }
        }
    }

    fun select(id: String?) {
        if (id == null) {
            _state.update { BotsUiState() }
            return
        }
        _state.update {
            it.copy(
                selectedId = id,
                manifest = null,
                persona = "",
                personaLoaded = false,
                permissions = null,
                crew = null,
                tasks = emptyList(),
                journal = emptyList(),
                inbox = emptyList(),
                detailLoading = true,
                dispatchFeedback = null,
            )
        }
        viewModelScope.launch {
            val detail = runCatching { api.botDetail(id) }.getOrNull()
            val persona = runCatching { api.botPersona(id) }.getOrNull()
            val permissions = runCatching { api.botPermissions(id) }.getOrNull()
            val crew = runCatching { api.botCrew(id) }.getOrNull()
            val tasks = runCatching { api.botTasks(id) }.getOrNull()
            val inbox = runCatching { api.botInbox(id) }.getOrNull()
            _state.update {
                it.copy(
                    manifest = detail?.bot,
                    liveState = detail?.state,
                    persona = (persona?.get("persona") as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    personaLoaded = true,
                    permissions = permissions,
                    crew = crew,
                    tasks = array(tasks, "tasks"),
                    inbox = array(inbox, "inbox"),
                    journal = detail?.journal ?: emptyList(),
                    detailLoading = false,
                )
            }
        }
    }

    private fun array(node: JsonObject?, key: String): List<JsonObject> =
        (node?.get(key) as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { entry -> runCatching { entry as JsonObject }.getOrNull() }
            ?: emptyList()

    /** Queues a message for the bot: the call returns in milliseconds, the work runs after. */
    fun dispatch(message: String) {
        val id = _state.value.selectedId ?: return
        if (message.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, dispatchFeedback = null, error = null) }
            runCatching { api.dispatchBot(id, message.trim()) }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            busy = false,
                            dispatchFeedback = if (result.accepted) {
                                "Mis en file (job ${result.jobId.take(12)}) — le fil principal continue."
                            } else {
                                "Refusé : ${result.reasonCode.ifBlank { "file saturée ou bot en pause" }}"
                            },
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.readable()) } }
        }
    }

    fun setEnabled(id: String, enabled: Boolean) = patch(id, """{"enabled":$enabled}""")

    fun setModel(id: String, model: String) = patch(id, """{"model":"${escape(model)}"}""")

    fun setDescription(id: String, description: String) = patch(id, """{"description":"${escape(description)}"}""")

    /** Daily token cap lives in `autonomy`; 0 means "no cap". */
    fun setDailyBudget(id: String, tokens: Int) =
        patch(id, """{"autonomy":{"dailyTokenBudget":${if (tokens > 0) tokens else 0}}}""")

    fun savePersona(text: String) {
        val id = _state.value.selectedId ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { api.setBotPersona(id, text) }
                .onSuccess { _state.update { s -> s.copy(busy = false, message = "Persona enregistré") } }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    fun setTier(id: String, tier: String) = act("Palier appliqué") { api.setBotPermissions(id, tier) }

    fun promote(id: String) = act("Promu chef de flotte") { api.promoteBot(id) }

    fun autoCrew(id: String) = act("Construction de la flotte demandée") { api.autoCrewBot(id) }

    fun addCrew(leadId: String, id: String, name: String, description: String?, persona: String?, tier: String?) =
        act("Membre ajouté") { api.addBotCrew(leadId, id, name, description, persona, tier) }

    fun start(id: String) = act("Équipier démarré") { api.startBot(id) }

    fun stop(id: String) = act("Équipier arrêté") { api.stopBot(id) }

    fun run(id: String) = act("Exécution demandée") { api.runBot(id) }

    fun delete(id: String) = act("Équipier supprimé") { api.deleteBot(id) }

    fun clearBanner() = _state.update { it.copy(message = null, error = null, dispatchFeedback = null) }

    private fun patch(id: String, patchJson: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching {
                api.patchBot(id, kotlinx.serialization.json.Json.parseToJsonElement(patchJson) as JsonObject)
            }
                .onSuccess { _state.update { s -> s.copy(busy = false, message = "Enregistré") } }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    private fun act(successMessage: String, block: suspend () -> Any?) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null, error = null) }
            runCatching { block() }
                .onSuccess { _state.update { s -> s.copy(busy = false, message = successMessage) } }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    private fun escape(text: String): String =
        text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    private fun Throwable.readable(): String = when (this) {
        is AuthException -> "Session expirée — reconnecte-toi"
        else -> message ?: "Requête refusée"
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { BotsViewModel(container.api, container) }
        }
    }
}
