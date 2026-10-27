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
import io.github.tournierjc.mercury.data.dto.BotCrewResponse
import io.github.tournierjc.mercury.data.dto.BotTierDto
import io.github.tournierjc.mercury.data.dto.BotsCostReport
import io.github.tournierjc.mercury.data.dto.BotsStorageReport
import io.github.tournierjc.mercury.data.dto.BrainStatusDto
import io.github.tournierjc.mercury.data.dto.CodeStatusDto
import io.github.tournierjc.mercury.data.dto.DeliverableDto
import io.github.tournierjc.mercury.data.dto.HealthDto
import io.github.tournierjc.mercury.data.dto.McpServersResponse
import io.github.tournierjc.mercury.data.dto.PluginDto
import io.github.tournierjc.mercury.data.dto.ProviderDto
import io.github.tournierjc.mercury.data.dto.ProviderTestResult
import io.github.tournierjc.mercury.data.dto.ProviderUpdateRequest
import io.github.tournierjc.mercury.data.dto.QueueCounts
import io.github.tournierjc.mercury.data.dto.SkillDto
import io.github.tournierjc.mercury.data.dto.StatusDto
import io.github.tournierjc.mercury.data.dto.TerminalResult
import io.github.tournierjc.mercury.data.dto.WorkspaceInfoDto
import io.github.tournierjc.mercury.util.OutputActions
import io.github.tournierjc.mercury.util.OutputPreviewState
import io.github.tournierjc.mercury.util.SharedFile
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
    val crew: BotCrewResponse? = null,
    val permissions: JsonObject? = null,
    val providers: List<ProviderDto> = emptyList(),
    val mcp: McpServersResponse? = null,
    val cost: BotsCostReport? = null,
    val storage: BotsStorageReport? = null,
    val providerTest: ProviderTestResult? = null,
    val providerTestFor: String = "",
    val bundle: String? = null,
    val bundleFor: String = "",
    val terminal: TerminalResult? = null,
    val terminalBusy: Boolean = false,
    val queue: QueueCounts = QueueCounts(),
    /** Fleet inbox: every bot's deliverables, newest first. */
    val outputs: List<DeliverableDto> = emptyList(),
    val outputPreview: OutputPreviewState? = null,
    val pendingShare: SharedFile? = null,
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

    /** Deliverable actions, shared with the bot sheet so a file behaves the same way. */
    private val outputActions = OutputActions(api)

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
                val outputs = runCatching { api.fleetOutputs() }.getOrNull()
                val providers = runCatching { api.providers() }.getOrDefault(emptyList())
                val mcp = runCatching { api.mcpServers() }.getOrNull()
                val cost = runCatching { api.botsCost() }.getOrNull()
                val storage = runCatching { api.botsStorage() }.getOrNull()
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
                        providers = providers,
                        mcp = mcp,
                        cost = cost,
                        storage = storage,
                        queue = bots?.queue ?: storage?.queue ?: QueueCounts(),
                        outputs = outputs?.outputs.orEmpty(),
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

    /** `POST /api/skills/install` — the daemon takes a URL, not a local folder. */
    fun installSkill(url: String) = action("Skill installé depuis $url") { api.installSkill(url) }

    /** `POST /api/skills/install-from-registry` — `id` is `<category>/<slug>`. */
    fun installSkillFromRegistry(id: String, force: Boolean) = action("Skill $id installé depuis le registre") {
        api.installSkillFromRegistry(id, force)
    }

    /** `DELETE /api/skills/:name` — confirmed by the caller: the folder leaves the host. */
    fun deleteSkill(name: String) = action("Skill $name supprimé") { api.deleteSkill(name) }

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

    /**
     * Stops the daemon. The follow-up refresh would hit a socket that is going away, so this
     * one reports the acknowledgement instead of reloading the console.
     */
    fun shutdown() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null, error = null) }
            runCatching { api.shutdown() }
                .onSuccess { result ->
                    if (result.ok) {
                        _state.update { s ->
                            s.copy(busy = false, message = "Arrêt accepté (pid ${result.pid}) — le démon s'arrête")
                        }
                    } else {
                        _state.update { s ->
                            s.copy(busy = false, error = result.error.ifBlank { "Arrêt refusé par le démon" })
                        }
                    }
                }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    /** `PUT /api/workspace/root` — the daemon refuses a path that does not exist. */
    fun setWorkspaceRoot(path: String) = action("Espace de travail : $path") { api.setWorkspaceRoot(path) }

    // -------------------------------------------------------------------
    // Providers: write a key, probe the endpoint
    // -------------------------------------------------------------------

    /** Absent fields keep their stored value; a saved key only takes effect at the next boot. */
    fun saveProvider(
        id: String,
        apiKey: String? = null,
        baseUrl: String? = null,
        model: String? = null,
        enabled: Boolean? = null,
    ) = action("Fournisseur $id enregistré — redémarrage requis") {
        api.saveProvider(
            id,
            ProviderUpdateRequest(
                apiKey = apiKey?.trim()?.ifBlank { null },
                baseUrl = baseUrl?.trim()?.ifBlank { null },
                model = model?.trim()?.ifBlank { null },
                enabled = enabled,
            ),
        )
    }

    /** The probe's answer, success or refusal, stays on screen next to the provider. */
    fun testProvider(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(providerTest = null, providerTestFor = id, error = null) }
            runCatching { api.testProvider(id) }
                .onSuccess { result -> _state.update { it.copy(providerTest = result, providerTestFor = id) } }
                .onFailure { e ->
                    _state.update {
                        it.copy(providerTest = ProviderTestResult(success = false, error = e.readable()), providerTestFor = id)
                    }
                }
        }
    }

    // -------------------------------------------------------------------
    // Bundles: export one bot, import a shared document
    // -------------------------------------------------------------------

    /** Fetches the bundle document; the screen hands it to the Android share sheet. */
    fun exportBundle(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { api.botBundle(id) }
                .onSuccess { text -> _state.update { it.copy(busy = false, bundle = text, bundleFor = id) } }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.readable()) } }
        }
    }

    fun clearBundle() {
        _state.update { it.copy(bundle = null, bundleFor = "") }
    }

    /** Imported bots start disabled on purpose: a shared bundle must never begin spending. */
    fun importBundle(bundleJson: String, overwrite: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null, error = null) }
            runCatching { api.importBotBundle(bundleJson, overwrite) }
                .onSuccess { response ->
                    val report = response.report
                    when {
                        response.error.isNotBlank() ->
                            _state.update { s -> s.copy(busy = false, error = response.error) }

                        report.created.isEmpty() -> _state.update { s ->
                            s.copy(busy = false, message = "Rien d'importé — ${report.skipped.size} équipier(s) déjà présent(s)")
                        }

                        else -> {
                            _state.update { s ->
                                s.copy(
                                    busy = false,
                                    message = "${report.created.size} équipier(s) importé(s), désactivé(s) : " +
                                        report.created.joinToString(", "),
                                )
                            }
                            refresh()
                        }
                    }
                }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    fun clearBanner() {
        _state.update { it.copy(message = null, error = null) }
    }

    // -------------------------------------------------------------------
    // Fleet inbox: every bot's deliverables, with the same three actions
    // the bot sheet offers
    // -------------------------------------------------------------------

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
                    _state.update { s -> s.copy(busy = false, message = "Livrable supprimé") }
                    refresh()
                }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
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

    // -------------------------------------------------------------------
    // Terminal: one command, at the owner's explicit request
    // -------------------------------------------------------------------

    /**
     * Runs a command through `POST /api/terminal/exec`. This is real code execution on the
     * host: the UI asks for confirmation on destructive commands before calling this.
     */
    fun runCommand(command: String, cwd: String?) {
        if (command.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(terminalBusy = true, terminal = null, error = null) }
            runCatching { api.execTerminal(command.trim(), cwd?.trim()?.ifBlank { null }) }
                .onSuccess { result -> _state.update { it.copy(terminalBusy = false, terminal = result) } }
                .onFailure { e -> _state.update { it.copy(terminalBusy = false, error = e.readable()) } }
        }
    }

    fun clearTerminal() {
        _state.update { it.copy(terminal = null) }
    }

    // -------------------------------------------------------------------
    // Fleet: promote a bot, read its crew, add members, change its tier
    // -------------------------------------------------------------------

    fun loadCrew(id: String) {
        viewModelScope.launch {
            runCatching { api.botCrew(id) }
                .onSuccess { crew -> _state.update { it.copy(crew = crew) } }
                .onFailure { e -> _state.update { it.copy(crew = BotCrewResponse(error = e.readable())) } }
        }
    }

    fun loadPermissions(id: String) {
        viewModelScope.launch {
            runCatching { api.botPermissions(id) }
                .onSuccess { permissions -> _state.update { it.copy(permissions = permissions) } }
                .onFailure { e -> _state.update { it.copy(error = e.readable()) } }
        }
    }

    fun promoteBot(id: String) = botAction("Équipier promu en chef de flotte") { api.promoteBot(id) }

    fun autoCrew(id: String) = botAction("Construction de la flotte demandée") { api.autoCrewBot(id) }

    fun setTier(id: String, tier: String) = botAction("Palier de permissions appliqué") {
        api.setBotPermissions(id, tier)
    }

    fun addCrew(
        leadId: String,
        id: String,
        name: String,
        description: String?,
        persona: String?,
        tier: String?,
    ) = botAction("Équipier ajouté à la flotte") {
        api.addBotCrew(leadId, id, name, description, persona, tier)
    }.also { loadCrew(leadId) }

    private fun botAction(successMessage: String, block: suspend () -> Any?) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null, error = null) }
            runCatching { block() }
                .onSuccess { _state.update { s -> s.copy(busy = false, message = successMessage) }; refresh() }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ConsoleViewModel(container.api, container) }
        }
    }
}
