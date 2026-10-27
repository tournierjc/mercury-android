package io.github.tournierjc.mercury.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.tournierjc.mercury.AppContainer
import io.github.tournierjc.mercury.data.Attachments
import io.github.tournierjc.mercury.data.AuthException
import io.github.tournierjc.mercury.data.BotJson
import io.github.tournierjc.mercury.data.BotOps
import io.github.tournierjc.mercury.data.MercuryApi
import io.github.tournierjc.mercury.data.PendingFile
import io.github.tournierjc.mercury.data.RunRow
import io.github.tournierjc.mercury.data.dto.BotCrewResponse
import io.github.tournierjc.mercury.data.dto.DeliverableDto
import io.github.tournierjc.mercury.data.dto.DlqJobDto
import io.github.tournierjc.mercury.data.dto.RunTranscriptDto
import io.github.tournierjc.mercury.util.OutputActions
import io.github.tournierjc.mercury.util.OutputPreviewState
import io.github.tournierjc.mercury.util.SharedFile
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
    /** Deliverables of the selected bot, newest first. */
    val outputs: List<DeliverableDto> = emptyList(),
    /** Runs, journal lines joined with the transcripts the daemon still keeps. */
    val runs: List<RunRow> = emptyList(),
    val dlq: List<DlqJobDto> = emptyList(),
    val runDetail: RunTranscriptDto? = null,
    val runDetailLoading: Boolean = false,
    /** The deliverable being previewed, with its text or its bytes. */
    val outputPreview: OutputPreviewState? = null,
    /** A downloaded deliverable the screen hands to the share sheet, then clears. */
    val pendingShare: SharedFile? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val dispatchFeedback: String? = null,
    val detailLoading: Boolean = false,
    /** Filled by the bundle download, consumed by the share sheet, then cleared. */
    val bundle: String? = null,
    val bundleFor: String = "",
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

    /** Deliverable actions, shared with the Console's fleet inbox. */
    private val outputActions = OutputActions(api)

    private val _state = MutableStateFlow(BotsUiState())
    val state: StateFlow<BotsUiState> = _state.asStateFlow()

    /** Faces by bot id. A bot without one keeps its monogram — nothing is invented. */
    private val _avatars = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    val avatars: StateFlow<Map<String, ByteArray>> = _avatars.asStateFlow()
    private val avatarInFlight = mutableSetOf<String>()

    init {
        watcher.start()
    }

    /**
     * Fetches the faces the roster says exist and we do not hold yet. Driven by
     * `hasAvatar`, so a Mercury without avatars costs zero requests.
     */
    fun loadAvatars(bots: List<JsonObject>) {
        bots.filter { BotJson.hasAvatar(it) }
            .map { BotJson.id(it) }
            .filter { id -> id.isNotBlank() && _avatars.value[id] == null && avatarInFlight.add(id) }
            .forEach { id ->
                viewModelScope.launch {
                    runCatching { api.botAvatar(id) }
                        .onSuccess { bytes -> if (bytes != null) _avatars.update { it + (id to bytes) } }
                        .onFailure { /* a missing face is not an error: the monogram stays */ }
                    avatarInFlight.remove(id)
                }
            }
    }

    /** Puts a picked photo on a bot. The bytes leave the phone already downscaled. */
    fun uploadAvatar(id: String, bytes: ByteArray) {
        viewModelScope.launch {
            runCatching { api.uploadBotAvatar(id, Attachments.encode(bytes)) }
                .onSuccess {
                    _avatars.update { it + (id to bytes) }
                    _state.update { s -> s.copy(message = "Photo enregistrée") }
                }
                .onFailure { e ->
                    // A 4xx here is the server refusing the bytes: say why, do not retry.
                    _state.update { s -> s.copy(error = e.readable()) }
                }
        }
    }

    fun removeAvatar(id: String) {
        viewModelScope.launch {
            runCatching { api.deleteBotAvatar(id) }
                .onSuccess {
                    _avatars.update { it - id }
                    _state.update { s -> s.copy(message = "Photo retirée") }
                }
                .onFailure { e -> _state.update { s -> s.copy(error = e.readable()) } }
        }
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
                outputs = emptyList(),
                runs = emptyList(),
                dlq = emptyList(),
                runDetail = null,
                outputPreview = null,
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
            val outputs = runCatching { api.botOutputs(id) }.getOrNull()
            val transcripts = runCatching { api.botRuns(id) }.getOrNull()
            val records = runCatching { api.botRunJournal(id) }.getOrNull()
            val dlq = runCatching { api.botDlq(id) }.getOrNull()
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
                    outputs = outputs?.outputs.orEmpty(),
                    runs = BotOps.mergeRuns(transcripts?.runs.orEmpty(), records.orEmpty()),
                    dlq = dlq?.dlq.orEmpty(),
                    detailLoading = false,
                )
            }
        }
    }

    private fun array(node: JsonObject?, key: String): List<JsonObject> =
        (node?.get(key) as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { entry -> runCatching { entry as JsonObject }.getOrNull() }
            ?: emptyList()

    /**
     * Queues a message for the bot: the call returns in milliseconds, the work runs after.
     *
     * Files can go alone — the route accepts an empty prompt when something is attached,
     * and the server captions the turn itself.
     */
    fun dispatch(message: String, files: List<PendingFile> = emptyList()) {
        val id = _state.value.selectedId ?: return
        if (message.isBlank() && files.isEmpty()) return
        val problem = Attachments.problemWith(files)
        if (problem != null) {
            _state.update { it.copy(error = problem) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, dispatchFeedback = null, error = null) }
            runCatching { api.dispatchBot(id, message.trim(), attachments = files.map { it.toUpload() }) }
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

    /**
     * `GET /api/bots/:id/bundle` — the shareable document. The screen hands it to the
     * share sheet as soon as it arrives, then clears it.
     */
    fun exportBundle(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { api.botBundle(id) }
                .onSuccess { text -> _state.update { it.copy(busy = false, bundle = text, bundleFor = id) } }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.readable()) } }
        }
    }

    fun clearBundle() = _state.update { it.copy(bundle = null, bundleFor = "") }

    fun clearBanner() = _state.update { it.copy(message = null, error = null, dispatchFeedback = null) }

    /** Surfaces a screen-side failure (share sheet, missing file) in the same banner. */
    fun showError(message: String) = _state.update { it.copy(error = message) }

    // -------------------------------------------------------------------
    // Deliverables: list, preview, share, remove (the fleet inbox reuses
    // the same OutputActions from the Console)
    // -------------------------------------------------------------------

    fun reloadOutputs() {
        val id = _state.value.selectedId ?: return
        viewModelScope.launch {
            runCatching { api.botOutputs(id) }
                .onSuccess { page -> _state.update { it.copy(outputs = page.outputs) } }
                .onFailure { e -> _state.update { it.copy(error = e.readable()) } }
        }
    }

    fun previewOutput(output: DeliverableDto) {
        val id = output.botId.ifBlank { _state.value.selectedId.orEmpty() }
        if (id.isBlank() || output.name.isBlank()) return
        _state.update { it.copy(outputPreview = OutputPreviewState(output.copy(botId = id), loading = true)) }
        viewModelScope.launch {
            val preview = outputActions.preview(output.copy(botId = id))
            _state.update { it.copy(outputPreview = preview) }
        }
    }

    fun dismissPreview() = _state.update { it.copy(outputPreview = null) }

    /** Downloads the bytes; the screen hands them to the share sheet and clears the slot. */
    fun shareOutput(output: DeliverableDto) {
        val id = output.botId.ifBlank { _state.value.selectedId.orEmpty() }
        if (id.isBlank() || output.name.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { outputActions.download(output.copy(botId = id)) }
                .onSuccess { file -> _state.update { it.copy(busy = false, pendingShare = file) } }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.readable()) } }
        }
    }

    fun consumeShare() = _state.update { it.copy(pendingShare = null) }

    fun deleteOutput(output: DeliverableDto) {
        val id = output.botId.ifBlank { _state.value.selectedId.orEmpty() }
        if (id.isBlank() || output.name.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { api.deleteOutput(id, output.name) }
                .onSuccess {
                    _state.update { s -> s.copy(busy = false, message = "Livrable supprimé") }
                    reloadOutputs()
                }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    // -------------------------------------------------------------------
    // Run health: the transcript of one run, replaying a dead letter
    // -------------------------------------------------------------------

    fun openRun(row: RunRow) {
        val id = _state.value.selectedId ?: return
        if (!row.hasTranscript || row.runId.isBlank()) return
        _state.update { it.copy(runDetailLoading = true, runDetail = null) }
        viewModelScope.launch {
            runCatching { api.botRun(id, row.runId) }
                .onSuccess { transcript -> _state.update { it.copy(runDetail = transcript, runDetailLoading = false) } }
                .onFailure { e -> _state.update { it.copy(runDetailLoading = false, error = e.readable()) } }
        }
    }

    fun closeRun() = _state.update { it.copy(runDetail = null, runDetailLoading = false) }

    /**
     * Re-queues one dead-lettered job. A refusal (`not_found`, the job was consumed by a
     * previous replay) is an answer: it lands in the error banner, not in a retry loop.
     */
    fun replayIncident(job: DlqJobDto) {
        val id = _state.value.selectedId ?: return
        if (job.id.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null, error = null) }
            runCatching { api.replayDlq(id, job.id) }
                .onSuccess { result ->
                    _state.update {
                        if (result.accepted) {
                            it.copy(busy = false, message = "Job relancé (${result.jobId.take(12)})")
                        } else {
                            it.copy(busy = false, error = "Relance refusée : ${BotOps.reasonLabel(result.reasonCode)}")
                        }
                    }
                    reloadIncidents()
                }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    /** Drops every dead letter of the fleet — the confirmation lives in the UI. */
    fun clearDlq() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null, error = null) }
            runCatching { api.clearDlq() }
                .onSuccess { response ->
                    _state.update { s ->
                        s.copy(busy = false, message = "${response.cleared} incident(s) effacé(s)")
                    }
                    reloadIncidents()
                }
                .onFailure { e -> _state.update { s -> s.copy(busy = false, error = e.readable()) } }
        }
    }

    private fun reloadIncidents() {
        val id = _state.value.selectedId ?: return
        viewModelScope.launch {
            runCatching { api.botDlq(id) }
                .onSuccess { page -> _state.update { it.copy(dlq = page.dlq) } }
                .onFailure { /* the banner already carries the failure that led here */ }
        }
    }

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
