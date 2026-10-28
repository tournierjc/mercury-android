package io.github.tournierjc.mercury.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.tournierjc.mercury.AppContainer
import io.github.tournierjc.mercury.data.GitJson
import io.github.tournierjc.mercury.data.MercuryApi
import io.github.tournierjc.mercury.data.Notice
import io.github.tournierjc.mercury.data.NoticeKey
import io.github.tournierjc.mercury.data.toNotice
import io.github.tournierjc.mercury.data.dto.GitCommitDto
import io.github.tournierjc.mercury.data.dto.GitFileDto
import io.github.tournierjc.mercury.data.dto.GitStatusDto
import io.github.tournierjc.mercury.data.dto.SimpleResponse
import io.github.tournierjc.mercury.data.dto.TerminalResult
import io.github.tournierjc.mercury.data.dto.WorkspaceInfoDto
import io.github.tournierjc.mercury.ui.workspace.WorkspaceEntry
import io.github.tournierjc.mercury.ui.workspace.WorkspacePath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WorkspaceTab { Files, Terminal, Git }

/**
 * One command of the session: what was typed, and what came back.
 *
 * [failure] is a transport or auth error — not the same thing as a command that ran and exited
 * non-zero, which the transcript shows with its own code.
 */
data class WorkspaceCommand(
    val command: String,
    val result: TerminalResult? = null,
    val failure: String? = null,
) {
    /** `true` only for a command that actually ran and exited 0. */
    val ok: Boolean get() = result?.let { it.success && it.exitCode == 0 } == true

    val output: String get() = result?.output.orEmpty()
}

/** A file open in the read-only panel. [text] is null when the bytes are not text. */
data class WorkspacePreview(
    val path: String,
    val name: String,
    val size: Long,
    val text: String? = null,
    val binary: Boolean = false,
    /** True when the file is longer than the panel shows, or the daemon cut it at 512 kB. */
    val truncated: Boolean = false,
    val lines: Int = 0,
)

data class WorkspaceUiState(
    val tab: WorkspaceTab = WorkspaceTab.Files,
    val info: WorkspaceInfoDto? = null,
    // ---- Fichiers: where we are, what is there, what is open
    val path: String = WorkspacePath.ROOT,
    val entries: List<WorkspaceEntry> = emptyList(),
    val preview: WorkspacePreview? = null,
    val filesLoading: Boolean = true,
    // ---- Terminal: the session, oldest first
    val commands: List<WorkspaceCommand> = emptyList(),
    // ---- Git: the repository, its index and its log
    val git: GitStatusDto? = null,
    val gitLog: List<GitCommitDto> = emptyList(),
    val commitDraft: String = "",
    val diff: String? = null,
    val diffPath: String? = null,
    val diffStaged: Boolean = false,
    /** One action at a time: every control is drawn disabled while this is true. */
    val busy: Boolean = false,
    /** What the last finished action says (a key, resolved by the screen). */
    val message: Notice? = null,
    val error: Notice? = null,
) {
    /** The staged side of the index, deduplicated the way the console card does it. */
    val staged: List<GitFileDto> get() = GitJson.staged(git)

    val unstaged: List<GitFileDto> get() = GitJson.unstaged(git)
}

/**
 * The workspace as the dashboard's `/workspace` page shows it: a file browser over `/work`, a
 * terminal that runs one command at a time, and the repository's index.
 *
 * Everything here is an explicit act — opening a folder, running a typed command, staging a named
 * path, committing a typed message. There is no delete and no force: the routes for those are not
 * called, so they cannot be reached by a mis-tap.
 */
class WorkspaceViewModel(private val api: MercuryApi) : ViewModel() {

    private val _state = MutableStateFlow(WorkspaceUiState())
    val state: StateFlow<WorkspaceUiState> = _state.asStateFlow()

    init {
        loadInfo()
        openDirectory(WorkspacePath.ROOT)
    }

    // -------------------------------------------------------------------
    // Tabs and banners
    // -------------------------------------------------------------------

    fun selectTab(tab: WorkspaceTab) {
        _state.update { it.copy(tab = tab, message = null, error = null) }
        // The git card is the one tab that costs requests: read it when it is first opened.
        if (tab == WorkspaceTab.Git && _state.value.git == null) refreshGit()
    }

    /** The header's refresh, doing what the current tab is about. */
    fun refresh() {
        when (_state.value.tab) {
            WorkspaceTab.Files -> openDirectory(_state.value.path)
            WorkspaceTab.Terminal -> Unit
            WorkspaceTab.Git -> refreshGit()
        }
    }

    fun clearBanner() {
        _state.update { it.copy(message = null, error = null) }
    }

    private fun loadInfo() {
        viewModelScope.launch {
            runCatching { api.workspaceInfo() }
                .onSuccess { info -> _state.update { it.copy(info = info) } }
        }
    }

    // -------------------------------------------------------------------
    // Fichiers: the tree, one directory at a time
    // -------------------------------------------------------------------

    /** Enters a directory, or the root when [path] is blank. */
    fun openDirectory(path: String) {
        val target = WorkspacePath.normalise(path)
        action {
            _state.update { it.copy(filesLoading = true) }
            try {
                val tree = api.workspaceTree(target.ifBlank { null })
                _state.update {
                    it.copy(
                        filesLoading = false,
                        path = target,
                        entries = WorkspacePath.sort(tree.items.mapNotNull { WorkspaceEntry.of(it) }),
                        preview = null,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(filesLoading = false, entries = emptyList(), path = target) }
                throw e
            }
        }
    }

    /** Tapping a row: a directory enters it, a file opens its preview. */
    fun open(entry: WorkspaceEntry) {
        if (entry.isDirectory) openDirectory(entry.path) else openFile(entry.path)
    }

    /** One level up; at the root this is a no-op, so the screen can leave instead. */
    fun up() {
        val current = _state.value.path
        if (current.isBlank()) return
        openDirectory(WorkspacePath.parent(current))
    }

    fun openFile(path: String) {
        action {
            val file = api.workspaceFile(path)
            val lines = file.content.lines()
            val binary = file.binary || WorkspacePath.looksBinary(file.content)
            _state.update {
                it.copy(
                    preview = WorkspacePreview(
                        path = file.path.ifBlank { path },
                        name = file.name.ifBlank { path.substringAfterLast('/') },
                        size = file.size,
                        text = if (binary) null else lines.take(PREVIEW_LINES).joinToString("\n"),
                        binary = binary,
                        truncated = file.truncated || lines.size > PREVIEW_LINES,
                        lines = lines.size.coerceAtMost(PREVIEW_LINES),
                    ),
                )
            }
        }
    }

    fun closePreview() {
        _state.update { it.copy(preview = null) }
    }

    // -------------------------------------------------------------------
    // Terminal: one command, sent verbatim
    // -------------------------------------------------------------------

    /**
     * `POST /api/terminal/exec`, in the workspace root.
     *
     * The string goes to the daemon as typed — the client does no quoting, expands nothing and
     * strips nothing, so what the transcript echoes is what ran.
     */
    fun runCommand(command: String) {
        if (command.isBlank()) return
        action {
            val entry = runCatching { api.execTerminal(command) }.fold(
                onSuccess = { WorkspaceCommand(command = command, result = it) },
                onFailure = { WorkspaceCommand(command = command, failure = it.message.orEmpty()) },
            )
            _state.update { it.copy(commands = (it.commands + entry).takeLast(MAX_COMMANDS)) }
        }
    }

    fun clearTerminal() {
        _state.update { it.copy(commands = emptyList()) }
    }

    // -------------------------------------------------------------------
    // Git: status, index, diff, commit, push/pull, log
    // -------------------------------------------------------------------

    fun refreshGit() {
        action { loadGit() }
    }

    /**
     * A workspace that is not a repository answers 500 on every git route: [WorkspaceUiState.git]
     * stays null and the tab says so, instead of barking a banner on every open.
     */
    private suspend fun loadGit() {
        val status = runCatching { api.gitStatus() }.getOrNull()
        val log = if (status == null) {
            emptyList<GitCommitDto>()
        } else {
            runCatching { api.gitLog(COMMIT_COUNT) }.getOrDefault(emptyList())
        }
        _state.update { it.copy(git = status, gitLog = log) }
    }

    fun stage(paths: List<String>) = stageOrUnstage(paths, staged = true)

    fun unstage(paths: List<String>) = stageOrUnstage(paths, staged = false)

    private fun stageOrUnstage(paths: List<String>, staged: Boolean) {
        val clean = paths.filter { it.isNotBlank() }
        if (clean.isEmpty()) return
        val done = when {
            staged && clean.size == 1 -> Notice(NoticeKey.FILE_STAGED)
            staged -> Notice(NoticeKey.FILES_STAGED, args = listOf(clean.size.toString()))
            clean.size == 1 -> Notice(NoticeKey.FILE_UNSTAGED)
            else -> Notice(NoticeKey.FILES_UNSTAGED, args = listOf(clean.size.toString()))
        }
        gitAction(success = { done }) { if (staged) api.gitStage(clean) else api.gitUnstage(clean) }
    }

    /** Every path on the unstaged side and no other — never the staged ones again. */
    fun stageAll() = stage(GitJson.stageable(_state.value.git))

    fun unstageAll() = unstage(GitJson.unstageable(_state.value.git))

    fun setCommitDraft(text: String) {
        _state.update { it.copy(commitDraft = text) }
    }

    /** Asks the runtime's own model for a message; the draft is replaced only on success. */
    fun generateCommitMessage() {
        action {
            val message = api.generateCommitMessage()
            _state.update { it.copy(commitDraft = message.trim()) }
        }
    }

    /** Commits what is staged, with the draft as-is: the daemon adds its own trailer. */
    fun commit() {
        val message = _state.value.commitDraft.trim()
        if (message.isEmpty()) return
        gitAction(
            success = { detail -> Notice(NoticeKey.COMMIT_CREATED).withDetail(detail) },
            onSuccess = { _state.update { it.copy(commitDraft = "") } },
        ) { api.gitCommit(message) }
    }

    fun push() = gitAction(success = { detail -> Notice(NoticeKey.PUSHED).withDetail(detail) }) { api.gitPush() }

    fun pull() = gitAction(success = { detail -> Notice(NoticeKey.PULLED).withDetail(detail) }) { api.gitPull() }

    /**
     * The patch of one file, on one side of the index. A route that answers with text, not a
     * schema: the screen draws it, it does not interpret it.
     */
    fun showDiff(path: String?, staged: Boolean) {
        action {
            val patch = api.gitDiff(path, staged)
            _state.update { it.copy(diff = patch, diffPath = path, diffStaged = staged) }
        }
    }

    fun dismissDiff() {
        _state.update { it.copy(diff = null, diffPath = null) }
    }

    /**
     * An index or commit action: run it, say what it did, then re-read the status so the lists
     * match the disk. The banner takes git's own words when it has any (`Everything up-to-date`),
     * because a blank success tells the owner less than the truth does.
     */
    private fun gitAction(
        success: (String) -> Notice,
        onSuccess: () -> Unit = {},
        block: suspend () -> SimpleResponse,
    ) = action {
        val response = block()
        onSuccess()
        loadGit()
        _state.update { it.copy(message = success(response.message)) }
    }

    // -------------------------------------------------------------------
    // The one door every action goes through
    // -------------------------------------------------------------------

    /**
     * One action at a time (the same discipline as the bridge screen): while one is in flight
     * every control is disabled, so a second tap cannot stage what the first is still committing.
     */
    private fun action(block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null, error = null) }
            try {
                block()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.readable()) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    /** One mapping for the whole app (see `toNotice`), no local copy of the wording. */
    private fun Throwable.readable(): Notice = toNotice()

    companion object {
        /** How much of a file the preview keeps: enough to recognise it, not a reader. */
        const val PREVIEW_LINES = 200

        /** The session keeps the whole scrollback of a phone-sized run. */
        private const val MAX_COMMANDS = 40

        private const val COMMIT_COUNT = 10

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { WorkspaceViewModel(container.api) }
        }
    }
}
