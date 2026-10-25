package io.github.tournierjc.mercury.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.tournierjc.mercury.AppContainer
import io.github.tournierjc.mercury.data.AuthException
import io.github.tournierjc.mercury.data.MercuryApi
import io.github.tournierjc.mercury.data.dto.BotDetailDto
import io.github.tournierjc.mercury.data.dto.BotTierDto
import io.github.tournierjc.mercury.data.dto.BrainStatusDto
import io.github.tournierjc.mercury.data.dto.CodeStatusDto
import io.github.tournierjc.mercury.data.dto.HealthDto
import io.github.tournierjc.mercury.data.dto.PluginDto
import io.github.tournierjc.mercury.data.dto.QueueCounts
import io.github.tournierjc.mercury.data.dto.SkillDto
import io.github.tournierjc.mercury.data.dto.StatusDto
import io.github.tournierjc.mercury.data.dto.WorkspaceInfoDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

data class ConsoleUiState(
    val loading: Boolean = true,
    val status: StatusDto? = null,
    val health: HealthDto? = null,
    val brain: BrainStatusDto? = null,
    val code: CodeStatusDto? = null,
    val workspace: WorkspaceInfoDto? = null,
    val skills: List<SkillDto> = emptyList(),
    val plugins: List<PluginDto> = emptyList(),
    val tasks: List<JsonObject> = emptyList(),
    val agents: List<JsonObject> = emptyList(),
    val schedules: List<JsonObject> = emptyList(),
    val bots: List<JsonObject> = emptyList(),
    val tiers: List<BotTierDto> = emptyList(),
    val botDetail: BotDetailDto? = null,
    val queue: QueueCounts = QueueCounts(),
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

/**
 * Supervision surface: runtime health, provider wiring, teammates (bots),
 * background tasks, schedules, skills and plugins — all read from the daemon so
 * the phone shows the same truth as the dashboard.
 */
class ConsoleViewModel(
    private val api: MercuryApi,
    private val container: AppContainer,
) : ViewModel() {

    private val _state = MutableStateFlow(ConsoleUiState())
    val state: StateFlow<ConsoleUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val status = api.status()
                val health = runCatching { api.healthDetail() }.getOrNull()
                val brain = runCatching { api.brainStatus() }.getOrNull()
                val code = runCatching { api.codeStatus() }.getOrNull()
                val workspace = runCatching { api.workspaceInfo() }.getOrNull()
                val skills = runCatching { api.skills() }.getOrDefault(emptyList())
                val plugins = runCatching { api.plugins() }.getOrDefault(emptyList())
                val tasks = runCatching { api.backgroundTasks() }.getOrNull()
                val agents = runCatching { api.agents() }.getOrNull()
                val schedules = runCatching { api.schedules() }.getOrDefault(emptyList())
                val bots = runCatching { api.bots() }.getOrNull()
                val tiers = runCatching { api.botTiers() }.getOrDefault(emptyList())
                _state.update {
                    it.copy(
                        loading = false,
                        status = status,
                        health = health,
                        brain = brain,
                        code = code,
                        workspace = workspace,
                        skills = skills,
                        plugins = plugins,
                        tasks = tasks?.tasks.orEmpty(),
                        agents = agents?.agents.orEmpty(),
                        schedules = schedules,
                        bots = bots?.bots.orEmpty(),
                        tiers = tiers,
                        queue = bots?.queue ?: QueueCounts(),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.readable()) }
            }
        }
    }

    fun cancelTask(id: String) = action("Tâche annulée") { api.cancelTask(id) }

    fun clearFinishedTasks() = action("Tâches terminées effacées") { api.clearFinishedTasks() }

    fun haltAgents() = action("Agents mis en pause") { api.haltAgents() }

    fun stopAgents() = action("Agents arrêtés") { api.stopAgents() }

    fun deleteSchedule(id: String) = action("Automatisation supprimée") { api.deleteSchedule(id) }

    fun toggleSkill(name: String, active: Boolean) = action(
        if (active) "Skill $name activé" else "Skill $name désactivé",
    ) { api.setSkillActive(name, active) }

    fun startBot(id: String) = action("Bot démarré") { api.startBot(id) }

    fun stopBot(id: String) = action("Bot arrêté") { api.stopBot(id) }

    fun runBot(id: String) = action("Exécution demandée") { api.runBot(id) }

    fun startAllBots() = action("Tous les bots démarrés") { api.startAllBots() }

    fun stopAllBots() = action("Tous les bots arrêtés") { api.stopAllBots() }

    fun setBotEnabled(id: String, enabled: Boolean) = action(
        if (enabled) "Bot $id activé" else "Bot $id désactivé",
    ) { api.setBotEnabled(id, enabled) }

    fun deleteBot(id: String) = action("Bot $id supprimé") { api.deleteBot(id) }

    fun createBot(id: String, name: String, description: String?, tier: String?) = action("Bot $name créé") {
        api.createBot(id, name, description?.trim()?.ifBlank { null }, tier)
    }

    /** Opens the detail sheet: manifest, live state and the last journal lines. */
    fun openBot(id: String) {
        viewModelScope.launch {
            runCatching { api.botDetail(id) }
                .onSuccess { detail -> _state.update { it.copy(botDetail = detail) } }
                .onFailure { e -> _state.update { it.copy(error = e.readable()) } }
        }
    }

    fun closeBot() {
        _state.update { it.copy(botDetail = null) }
    }

    fun setCodeMode(state: String) = action("Mode programmation : $state") { api.setCodeMode(state) }

    /** Restarts the daemon: the HTTP call returns before the socket actually drops. */
    fun restart() = action("Redémarrage accepté — le runtime revient dans quelques secondes") { api.restart() }

    fun clearBanner() {
        _state.update { it.copy(message = null, error = null) }
    }

    private fun action(successMessage: String, block: suspend () -> Any?) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null, error = null) }
            runCatching { block() }
                .onSuccess { _state.update { s -> s.copy(busy = false, message = successMessage) }; refresh() }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    private fun Throwable.readable(): String = when (this) {
        is AuthException -> "Session expirée — reconnecte-toi"
        else -> message ?: "Requête refusée"
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ConsoleViewModel(container.api, container) }
        }
    }
}
