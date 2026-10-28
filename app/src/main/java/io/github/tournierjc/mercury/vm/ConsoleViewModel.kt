package io.github.tournierjc.mercury.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.tournierjc.mercury.AppContainer
import io.github.tournierjc.mercury.data.AuthException
import io.github.tournierjc.mercury.data.GitJson
import io.github.tournierjc.mercury.data.MercuryApi
import io.github.tournierjc.mercury.data.NowPlaying
import io.github.tournierjc.mercury.data.SpotifyDevice
import io.github.tournierjc.mercury.data.SpotifyJson
import io.github.tournierjc.mercury.data.dto.GitBranchDto
import io.github.tournierjc.mercury.data.dto.GitCommitDto
import io.github.tournierjc.mercury.data.dto.GitStatusDto
import io.github.tournierjc.mercury.data.dto.SpotifyStatusDto
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
import io.github.tournierjc.mercury.data.BotJson
import io.github.tournierjc.mercury.data.Notice
import io.github.tournierjc.mercury.data.NoticeKey
import io.github.tournierjc.mercury.data.toNotice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * One line of the terminal session: the command as typed, and what came back.
 *
 * `failure` carries a transport/auth error, which is not the same thing as a command that ran and
 * exited non-zero — the transcript shows them differently.
 */
data class TerminalEntry(
    val command: String,
    val result: TerminalResult? = null,
    val failure: String? = null,
) {
    val ok: Boolean get() = result?.success == true
    val body: String get() = result?.display ?: failure.orEmpty()
    /** The line under the transcript: a key, so the screen says it in the owner's language. */
    val status: Notice?
        get() = when {
            failure != null -> Notice(NoticeKey.TERMINAL_REQUEST_FAILED)
            result == null -> null
            result.exitCode != 0 -> Notice(NoticeKey.TERMINAL_EXIT_CODE, args = listOf(result.exitCode.toString()))
            else -> Notice(NoticeKey.TERMINAL_OK).asInfo()
        }
}

data class ConsoleUiState(
    val loading: Boolean = true,
    val status: StatusDto? = null,
    val health: HealthDto? = null,
    val brain: BrainStatusDto? = null,
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
    val bundle: String? = null,
    val bundleFor: String = "",
    val terminal: TerminalResult? = null,
    val terminalBusy: Boolean = false,
    /**
     * The session: one entry per command, in order. A terminal is a transcript, not a result —
     * the previous version replaced the answer on every run, so you could never read two commands
     * together or scroll back.
     */
    val terminalLog: List<TerminalEntry> = emptyList(),
    /** Where the next command runs, relative to the workspace. */
    val terminalCwd: String = "",
    val queue: QueueCounts = QueueCounts(),
    /** Fleet inbox: every bot's deliverables, newest first. */
    val outputs: List<DeliverableDto> = emptyList(),
    val outputPreview: OutputPreviewState? = null,
    val pendingShare: SharedFile? = null,
    val busy: Boolean = false,
    /** What the last finished action says (a key, resolved by the screen). */
    val message: Notice? = null,
    val error: Notice? = null,
    // Git: the workspace repository, driven from the console.
    val git: GitStatusDto? = null,
    val gitBranches: List<GitBranchDto> = emptyList(),
    val gitLog: List<GitCommitDto> = emptyList(),
    val commitDraft: String = "",
    val gitDiff: String? = null,
    /** Which side of the index the patch came from, and the path when there was one. */
    val gitDiffStaged: Boolean = false,
    val gitDiffPath: String? = null,
    val gitBusy: Boolean = false,
    // Spotify: a convenience surface, not a music app.
    val music: SpotifyStatusDto? = null,
    val nowPlaying: NowPlaying? = null,
    val devices: List<SpotifyDevice> = emptyList(),
    val musicBusy: Boolean = false,
)

/**
 * Supervision surface: runtime health, provider wiring, teammates (bots),
 * background tasks, schedules, skills and plugins — all read from the daemon so
 * the phone shows the same truth as the dashboard.
 */
/** Faces by bot id, loaded once per id: the fleet's deliverables group by bot. */
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

    private val _avatars = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    val avatars: StateFlow<Map<String, ByteArray>> = _avatars.asStateFlow()
    private val avatarInFlight = mutableSetOf<String>()

    /** Fetches only the faces the roster says exist, once per bot. */
    fun loadAvatars(bots: List<kotlinx.serialization.json.JsonObject>) {
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

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val status = api.status()
                val health = runCatching { api.healthDetail() }.getOrNull()
                val brain = runCatching { api.brainStatus() }.getOrNull()
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
                loadGit()
                loadMusic()
                loadAvatars(bots?.bots.orEmpty())
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.readable()) }
            }
        }
    }

    fun cancelTask(id: String) = action(Notice(NoticeKey.TASK_CANCELLED)) { api.cancelTask(id) }

    fun clearFinishedTasks() = action(Notice(NoticeKey.FINISHED_TASKS_CLEARED)) { api.clearFinishedTasks() }

    fun haltAgents() = action(Notice(NoticeKey.AGENTS_HALTED)) { api.haltAgents() }

    fun stopAgents() = action(Notice(NoticeKey.AGENTS_STOPPED)) { api.stopAgents() }

    fun deleteSchedule(id: String) = action(Notice(NoticeKey.SCHEDULE_DELETED)) { api.deleteSchedule(id) }

    fun toggleSkill(name: String, active: Boolean) = action(
        if (active) Notice(NoticeKey.SKILL_ENABLED, args = listOf(name)) else Notice(NoticeKey.SKILL_DISABLED, args = listOf(name)),
    ) { api.setSkillActive(name, active) }

    /** `POST /api/skills/install` — the daemon takes a URL, not a local folder. */
    fun installSkill(url: String) = action(Notice(NoticeKey.SKILL_INSTALLED_FROM_URL, args = listOf(url))) { api.installSkill(url) }

    /** `POST /api/skills/install-from-registry` — `id` is `<category>/<slug>`. */
    fun installSkillFromRegistry(id: String, force: Boolean) = action(Notice(NoticeKey.SKILL_INSTALLED_FROM_REGISTRY, args = listOf(id))) {
        api.installSkillFromRegistry(id, force)
    }

    /** `DELETE /api/skills/:name` — confirmed by the caller: the folder leaves the host. */
    fun deleteSkill(name: String) = action(Notice(NoticeKey.SKILL_DELETED, args = listOf(name))) { api.deleteSkill(name) }

    fun startBot(id: String) = action(Notice(NoticeKey.BOT_STARTED)) { api.startBot(id) }

    fun stopBot(id: String) = action(Notice(NoticeKey.BOT_STOPPED)) { api.stopBot(id) }

    fun runBot(id: String) = action(Notice(NoticeKey.RUN_REQUESTED)) { api.runBot(id) }

    fun startAllBots() = action(Notice(NoticeKey.BOTS_ALL_STARTED)) { api.startAllBots() }

    fun stopAllBots() = action(Notice(NoticeKey.BOTS_ALL_STOPPED)) { api.stopAllBots() }

    fun setBotEnabled(id: String, enabled: Boolean) = action(
        if (enabled) Notice(NoticeKey.TEAMMATE_ENABLED, args = listOf(id)) else Notice(NoticeKey.TEAMMATE_DISABLED, args = listOf(id)),
    ) { api.setBotEnabled(id, enabled) }

    fun deleteBot(id: String) = action(Notice(NoticeKey.TEAMMATE_DELETED_NAMED, args = listOf(id))) { api.deleteBot(id) }

    fun createBot(id: String, name: String, description: String?, tier: String?) = action(Notice(NoticeKey.TEAMMATE_CREATED, args = listOf(name))) {
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

    /** Restarts the daemon: the HTTP call returns before the socket actually drops. */
    fun restart() = action(Notice(NoticeKey.DAEMON_RESTART_REQUESTED)) { api.restart() }

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
                            s.copy(busy = false, message = Notice(NoticeKey.SHUTDOWN_ACCEPTED, args = listOf(result.pid.toString())))
                        }
                    } else {
                        _state.update { s ->
                            s.copy(busy = false, error = Notice(NoticeKey.SHUTDOWN_REFUSED).withDetail(result.error))
                        }
                    }
                }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    /** `PUT /api/workspace/root` — the daemon refuses a path that does not exist. */
    fun setWorkspaceRoot(path: String) = action(Notice(NoticeKey.WORKSPACE_SET, args = listOf(path))) { api.setWorkspaceRoot(path) }

    // -------------------------------------------------------------------
    // Providers: write a key, probe the endpoint
    // -------------------------------------------------------------------

    /** Absent fields keep their stored value; a saved key only takes effect at the next boot. */
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
                            _state.update { s -> s.copy(busy = false, error = Notice(NoticeKey.CREW_IMPORT_FAILED).withDetail(response.error)) }

                        report.created.isEmpty() -> _state.update { s ->
                            s.copy(busy = false, message = Notice(NoticeKey.CREW_IMPORT_NOTHING, args = listOf(report.skipped.size.toString())))
                        }

                        else -> {
                            _state.update { s ->
                                s.copy(
                                    busy = false,
                                    message = Notice(
                                        NoticeKey.CREW_IMPORTED,
                                        args = listOf(
                                            report.created.size.toString(),
                                            report.created.joinToString(", "),
                                        ),
                                    ),
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
                    _state.update { s -> s.copy(busy = false, message = Notice(NoticeKey.OUTPUT_DELETED)) }
                    refresh()
                }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    private fun action(success: Notice, block: suspend () -> Any?) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null, error = null) }
            runCatching { block() }
                .onSuccess { _state.update { s -> s.copy(busy = false, message = success) }; refresh() }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    /** One mapping for the whole app (see `toNotice`). */
    private fun Throwable.readable(): Notice = toNotice()

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
                .onSuccess { result ->
                    _state.update { s ->
                        s.copy(
                            terminalBusy = false,
                            terminal = result,
                            terminalLog = (s.terminalLog + TerminalEntry(command = command, result = result)).takeLast(60),
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { s ->
                        s.copy(
                            terminalBusy = false,
                            error = e.readable(),
                            // The entry mirrors the transport error as text: the banner carries the notice.
                            terminalLog = (s.terminalLog + TerminalEntry(command = command, failure = e.message.orEmpty()))
                                .takeLast(60),
                        )
                    }
                }
        }
    }

    fun setTerminalCwd(cwd: String) {
        _state.update { it.copy(terminalCwd = cwd.trim().trimStart('/')) }
    }

    fun clearTerminal() {
        _state.update { it.copy(terminal = null, terminalLog = emptyList()) }
    }

    // -------------------------------------------------------------------
    // Fleet: promote a bot, read its crew, add members, change its tier
    // -------------------------------------------------------------------

    fun loadCrew(id: String) {
        viewModelScope.launch {
            runCatching { api.botCrew(id) }
                .onSuccess { crew -> _state.update { it.copy(crew = crew) } }
                .onFailure { e -> _state.update { it.copy(crew = BotCrewResponse(error = e.message.orEmpty())) } }
        }
    }

    fun loadPermissions(id: String) {
        viewModelScope.launch {
            runCatching { api.botPermissions(id) }
                .onSuccess { permissions -> _state.update { it.copy(permissions = permissions) } }
                .onFailure { e -> _state.update { it.copy(error = e.readable()) } }
        }
    }

    fun promoteBot(id: String) = botAction(Notice(NoticeKey.PROMOTED_LEAD)) { api.promoteBot(id) }

    fun autoCrew(id: String) = botAction(Notice(NoticeKey.CREW_AUTOBUILD_REQUESTED)) { api.autoCrewBot(id) }

    fun setTier(id: String, tier: String) = botAction(Notice(NoticeKey.TIER_APPLIED)) {
        api.setBotPermissions(id, tier)
    }

    fun addCrew(
        leadId: String,
        id: String,
        name: String,
        description: String?,
        persona: String?,
        tier: String?,
    ) = botAction(Notice(NoticeKey.CREW_MEMBER_ADDED)) {
        api.addBotCrew(leadId, id, name, description, persona, tier)
    }.also { loadCrew(leadId) }

    private fun botAction(success: Notice, block: suspend () -> Any?) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null, error = null) }
            runCatching { block() }
                .onSuccess { _state.update { s -> s.copy(busy = false, message = success) }; refresh() }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    // -------------------------------------------------------------------
    // Git: status, index, commit, push/pull, branches, log
    // -------------------------------------------------------------------

    fun refreshGit() {
        viewModelScope.launch { loadGit() }
    }

    /**
     * Commits and failures both land here. The daemon answers `{error: <git stderr>}`, and
     * [readable] keeps that text whole: "nothing to commit, working tree clean" is more
     * useful to the owner than anything this client could paraphrase.
     */
    private fun gitAction(success: Notice, onSuccess: () -> Unit = {}, block: suspend () -> Any?) {
        viewModelScope.launch {
            _state.update { it.copy(gitBusy = true, message = null, error = null) }
            runCatching { block() }
                .onSuccess {
                    onSuccess()
                    _state.update { s -> s.copy(gitBusy = false, message = success) }
                    loadGit()
                }
                .onFailure { e -> _state.update { s -> s.copy(gitBusy = false, error = e.readable()) } }
        }
    }

    private suspend fun loadGit() {
        // A workspace that is not a repository answers 500 on every route: the card then
        // says so, but the rest of the console keeps working.
        val status = runCatching { api.gitStatus() }.getOrNull()
        val branches = if (status != null) runCatching { api.gitBranches() }.getOrDefault(emptyList()) else emptyList()
        val log = if (status != null) runCatching { api.gitLog(8) }.getOrDefault(emptyList()) else emptyList()
        _state.update { it.copy(git = status, gitBranches = branches, gitLog = log) }
    }

    fun stage(paths: List<String>) {
        if (paths.isEmpty()) return
        gitAction(
        if (paths.size == 1) Notice(NoticeKey.FILE_STAGED)
        else Notice(NoticeKey.FILES_STAGED, args = listOf(paths.size.toString())),
    ) {
            api.gitStage(paths)
        }
    }

    fun unstage(paths: List<String>) {
        if (paths.isEmpty()) return
        gitAction(
        if (paths.size == 1) Notice(NoticeKey.FILE_UNSTAGED)
        else Notice(NoticeKey.FILES_UNSTAGED, args = listOf(paths.size.toString())),
    ) {
            api.gitUnstage(paths)
        }
    }

    /** `Tout indexer` — every path on the unstaged side of the status, and no other. */
    fun stageAll() = stage(GitJson.stageable(_state.value.git))

    fun unstageAll() = unstage(GitJson.unstageable(_state.value.git))

    fun setCommitDraft(text: String) {
        _state.update { it.copy(commitDraft = text) }
    }

    /** Asks the runtime's model to write the message; the draft is replaced only on success. */
    fun generateCommitMessage() {
        viewModelScope.launch {
            _state.update { it.copy(gitBusy = true, message = null, error = null) }
            runCatching { api.generateCommitMessage() }
                .onSuccess { message -> _state.update { it.copy(gitBusy = false, commitDraft = message) } }
                .onFailure { e -> _state.update { it.copy(gitBusy = false, error = e.readable()) } }
        }
    }

    /** Commits the index with the draft as-is; the daemon adds its own trailer. */
    fun commit() {
        val message = _state.value.commitDraft.trim()
        if (message.isEmpty()) return
        gitAction(Notice(NoticeKey.COMMIT_CREATED), onSuccess = { _state.update { it.copy(commitDraft = "") } }) {
            api.gitCommit(message)
        }
    }

    fun push() = gitAction(Notice(NoticeKey.PUSHED)) { api.gitPush() }

    fun pull() = gitAction(Notice(NoticeKey.PULLED)) { api.gitPull() }

    fun checkout(branch: String) = gitAction(Notice(NoticeKey.BRANCH_CHECKED_OUT, args = listOf(branch))) { api.gitCheckout(branch) }

    /**
     * The diff route, shown as monospace text in a dialog. Nothing parses the patch here:
     * a renderer is a separate concern.
     */
    fun showDiff(path: String?, staged: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(gitBusy = true, message = null, error = null) }
            runCatching { api.gitDiff(path, staged) }
                .onSuccess { diff ->
                    val where = path?.takeIf { it.isNotBlank() }
                    _state.update {
                        it.copy(gitBusy = false, gitDiff = diff, gitDiffStaged = staged, gitDiffPath = where)
                    }
                }
                .onFailure { e -> _state.update { s -> s.copy(gitBusy = false, error = e.readable()) } }
        }
    }

    fun dismissDiff() {
        _state.update { it.copy(gitDiff = null, gitDiffPath = null) }
    }

    // -------------------------------------------------------------------
    // Musique: what plays, transport, volume, devices
    // -------------------------------------------------------------------

    fun refreshMusic() {
        viewModelScope.launch { loadMusic() }
    }

    private suspend fun loadMusic() {
        val status = runCatching { api.spotifyStatus() }.getOrNull()
        if (status?.connected != true) {
            _state.update { it.copy(music = status, nowPlaying = null, devices = emptyList()) }
            return
        }
        val line = runCatching { api.spotifyNowPlaying() }.getOrNull()
        val devices = runCatching { api.spotifyDevices() }.getOrDefault(emptyList())
        _state.update {
            it.copy(music = status, nowPlaying = SpotifyJson.nowPlaying(line?.text), devices = devices)
        }
    }

    /**
     * Transport controls. A refusal (free account, no active device) is shown verbatim too;
     * success is not announced — pressing "Suivant" needs no banner.
     */
    private fun musicAction(block: suspend () -> Any?) {
        viewModelScope.launch {
            _state.update { it.copy(musicBusy = true, message = null, error = null) }
            runCatching { block() }
                .onSuccess {
                    _state.update { s -> s.copy(musicBusy = false) }
                    loadMusic()
                }
                .onFailure { e -> _state.update { s -> s.copy(musicBusy = false, error = e.readable()) } }
        }
    }

    fun play() = musicAction { api.spotifyPlay() }

    fun pause() = musicAction { api.spotifyPause() }

    fun next() = musicAction { api.spotifyNext() }

    fun previous() = musicAction { api.spotifyPrevious() }

    fun setVolume(volume: Int) = musicAction { api.spotifyVolume(volume) }

    fun setShuffle(state: Boolean) = musicAction { api.spotifyShuffle(state) }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ConsoleViewModel(container.api, container) }
        }
    }
}
