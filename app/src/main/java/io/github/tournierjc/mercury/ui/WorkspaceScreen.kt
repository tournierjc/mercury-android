package io.github.tournierjc.mercury.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tournierjc.mercury.MercuryApp
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.data.GitJson
import io.github.tournierjc.mercury.data.dto.GitFileDto
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.ui.workspace.WorkspaceEntry
import io.github.tournierjc.mercury.ui.workspace.WorkspacePath
import io.github.tournierjc.mercury.vm.WorkspaceCommand
import io.github.tournierjc.mercury.vm.WorkspacePreview
import io.github.tournierjc.mercury.vm.WorkspaceTab
import io.github.tournierjc.mercury.vm.WorkspaceUiState
import io.github.tournierjc.mercury.vm.WorkspaceViewModel

/**
 * The workspace, as the dashboard's `/workspace` page draws it: what is in `/work`, a terminal that
 * runs one command at a time, and the repository's index — three tabs over one screen, because a
 * phone has no room for three panels side by side.
 *
 * Nothing here destroys anything: there is no delete, no reset and no force push, and the three
 * actions that touch the repository (commit, push, pull) ask first.
 */
@Composable
fun WorkspaceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MercuryApp).container }
    val viewModel: WorkspaceViewModel = viewModel(factory = WorkspaceViewModel.factory(container))
    val state by viewModel.state.collectAsState()
    val colors = LocalMercuryColors.current

    // Back is a directory first, then the screen: leaving the workspace from inside a folder is
    // never what the thumb meant.
    val goBack: () -> Unit = {
        when {
            state.preview != null -> viewModel.closePreview()
            state.path.isNotBlank() -> viewModel.up()
            else -> onBack()
        }
    }
    BackHandler(enabled = state.preview != null || state.path.isNotBlank()) { goBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        ScreenHeader(
            title = stringResource(R.string.workspace_titre),
            subtitle = state.info?.let { info -> info.projectName.ifBlank { info.cwd } },
            onBack = goBack,
            // The terminal tab has nothing to re-read: it is live, and a button that did nothing
            // there is exactly what made the header's refresh feel broken.
            onRefresh = if (state.tab == WorkspaceTab.Terminal) null else viewModel::refresh,
            refreshing = state.busy,
        )
        state.message?.let { notice -> NoticeBanner(notice, onDismiss = viewModel::clearBanner) }
        state.error?.let { notice -> NoticeBanner(notice, onDismiss = viewModel::clearBanner) }

        SegmentedTabs(
            labels = WorkspaceTab.entries.map { it.label() },
            selected = state.tab.ordinal,
            onSelect = { index -> viewModel.selectTab(WorkspaceTab.entries[index]) },
        )

        // One request at a time: the line says why every control went quiet.
        if (state.busy) {
            Text(
                text = stringResource(R.string.common_loading),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            when (state.tab) {
                WorkspaceTab.Files -> FilesTab(state, viewModel)
                WorkspaceTab.Terminal -> TerminalTab(state, viewModel)
                WorkspaceTab.Git -> GitTab(state, viewModel)
            }
            Text(
                text = " ",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

/** The label of each tab, one place, so the drawer of this screen cannot disagree with itself. */
@Composable
private fun WorkspaceTab.label(): String = when (this) {
    WorkspaceTab.Files -> stringResource(R.string.workspace_onglet_fichiers)
    WorkspaceTab.Terminal -> stringResource(R.string.workspace_onglet_terminal)
    WorkspaceTab.Git -> stringResource(R.string.workspace_onglet_git)
}

// ═══════════════════════════════════════════════════════════════════════
//  Fichiers
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun ColumnScope.FilesTab(state: WorkspaceUiState, viewModel: WorkspaceViewModel) {
    // `›` between crumbs, not `/`: the root's own label is `/`, and `/ / src` reads as a typo.
    Breadcrumbs(path = state.path, enabled = !state.busy, onOpen = viewModel::openDirectory)

    val preview = state.preview
    if (preview != null) {
        PreviewPanel(preview, onClose = viewModel::closePreview)
        return
    }

    when {
        state.filesLoading -> QuietLine(stringResource(R.string.common_loading))
        state.entries.isEmpty() -> QuietLine(stringResource(R.string.workspace_dossier_vide))
        else -> state.entries.forEach { entry ->
            EntryRow(entry = entry, enabled = !state.busy, onClick = { viewModel.open(entry) })
        }
    }
}

@Composable
private fun Breadcrumbs(path: String, enabled: Boolean, onOpen: (String) -> Unit) {
    val colors = LocalMercuryColors.current
    val crumbs = remember(path) { WorkspacePath.breadcrumb(path) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 2.dp),
    ) {
        crumbs.forEachIndexed { index, crumb ->
            if (index > 0) {
                Text(
                    text = "›",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
            val last = index == crumbs.lastIndex
            Text(
                text = crumb.label,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = if (last) colors.textPrimary else colors.accent,
                maxLines = 1,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(enabled = enabled && !last) { onOpen(crumb.path) }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            )
        }
    }
}

/** A folder or a file: the glyph says which, the second line says how big. */
@Composable
private fun EntryRow(entry: WorkspaceEntry, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalMercuryColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(colors.surface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Icon(
            imageVector = if (entry.isDirectory) Icons.Rounded.Folder else Icons.Rounded.Description,
            contentDescription = stringResource(
                if (entry.isDirectory) R.string.workspace_dossier_ouvrir else R.string.workspace_fichier_ouvrir,
            ),
            tint = if (entry.isDirectory) colors.accent else colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier
            .weight(1f)
            .padding(start = 10.dp)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!entry.isDirectory) {
                // The tree route carries no mtime, so the second line is what it does carry.
                val detail = listOfNotNull(
                    WorkspacePath.formatSize(entry.size),
                    entry.ext.removePrefix(".").ifBlank { null },
                ).joinToString(" · ")
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * A file, read-only.
 *
 * Monospace, selectable, and honest about what is missing: a binary file says so instead of
 * printing mojibake, and a preview that stops says where.
 */
@Composable
private fun PreviewPanel(preview: WorkspacePreview, onClose: () -> Unit) {
    val colors = LocalMercuryColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(colors.surface)
            .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.workspace_apercu_titre),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
                Text(
                    text = preview.path,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        WorkspacePath.formatSize(preview.size),
                        preview.text?.let {
                            pluralStringResource(R.plurals.workspace_apercu_lignes, preview.lines, preview.lines)
                        },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.workspace_apercu_fermer),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        val text = preview.text
        if (text == null) {
            Text(
                text = stringResource(R.string.workspace_apercu_binaire),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 10.dp),
            )
        } else {
            SelectionContainer {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.textPrimary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        if (preview.truncated) {
            Text(
                text = stringResource(R.string.workspace_apercu_tronque),
                style = MaterialTheme.typography.labelSmall,
                color = colors.accent,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  Terminal
// ═══════════════════════════════════════════════════════════════════════

/**
 * A terminal, not a form: the command echoed, its output under it, the exit code beside it, and a
 * history you can re-run with a thumb.
 *
 * The string goes to the daemon exactly as typed — no quoting, no expansion — so what the
 * transcript shows is what ran.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.TerminalTab(state: WorkspaceUiState, viewModel: WorkspaceViewModel) {
    val colors = LocalMercuryColors.current
    var line by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    // The field has no visible label — the `$` is the label. Screen readers still need a name.
    val commandLabel = stringResource(R.string.workspace_terminal_commande)

    // New output: keep the prompt in view, the way a terminal does.
    LaunchedEffect(state.commands.size, state.busy) {
        scroll.animateScrollTo(scroll.maxValue)
    }

    fun submit() {
        if (line.isBlank()) return
        viewModel.runCommand(line)
        line = ""
    }

    Text(
        text = stringResource(R.string.workspace_terminal_aide),
        style = MaterialTheme.typography.labelSmall,
        color = colors.textSecondary,
        modifier = Modifier.padding(top = 6.dp),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(colors.surfaceMuted)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp, max = 560.dp)
                .verticalScroll(scroll),
        ) {
            if (state.commands.isEmpty()) {
                Text(
                    text = stringResource(R.string.workspace_terminal_vide),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.textSecondary,
                )
            }
            state.commands.forEach { entry -> CommandBlock(entry) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Text(
                text = "$",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = colors.accent,
            )
            BasicTextField(
                value = line,
                onValueChange = { line = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = colors.textPrimary,
                ),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
                    .semantics { contentDescription = commandLabel },
            )
            // Clearing the transcript is a destructive act: it keeps the danger colour and sits
            // right where the hand already is, instead of a bar of its own under the terminal.
            IconButton(
                onClick = viewModel::clearTerminal,
                enabled = state.commands.isNotEmpty() && !state.busy,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    Icons.Rounded.DeleteSweep,
                    contentDescription = stringResource(R.string.workspace_terminal_effacer),
                    tint = if (state.commands.isNotEmpty() && !state.busy) colors.danger else colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = { submit() },
                enabled = line.isNotBlank() && !state.busy,
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.workspace_terminal_executer),
                    tint = if (line.isNotBlank() && !state.busy) colors.accent else colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }

    val history = remember(state.commands) {
        state.commands.map { it.command }.distinct().reversed().take(8)
    }
    if (history.isNotEmpty()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, start = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.workspace_terminal_historique).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.workspace_terminal_relancer),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            history.forEach { command ->
                Text(
                    text = command,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(colors.surface)
                        .clickable(enabled = !state.busy) { viewModel.runCommand(command) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }

    // Breathing room where the clear bar used to sit: the terminal tab ends on its history.
    Spacer(Modifier.size(4.dp))
}

@Composable
private fun CommandBlock(entry: WorkspaceCommand) {
    val colors = LocalMercuryColors.current
    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 10.dp)) {
        Text(text = "$ ${entry.command}", style = mono, color = colors.accent)
        val failure = entry.failure
        when {
            failure != null -> Text(
                text = "${stringResource(R.string.workspace_terminal_echec)} — $failure",
                style = mono,
                color = colors.danger,
            )

            // Both streams empty and the command worked: silence would read as "still running".
            entry.output.isBlank() -> Text(
                text = stringResource(R.string.workspace_terminal_sortie_vide),
                style = mono,
                color = colors.textSecondary,
            )

            // On a phone the first thing you want from a terminal is to copy what it said.
            else -> SelectionContainer {
                Text(
                    text = entry.output.trimEnd(),
                    style = mono,
                    color = if (entry.ok) colors.textPrimary else colors.danger,
                )
            }
        }
        entry.result?.let { result ->
            Text(
                text = stringResource(R.string.workspace_terminal_code, result.exitCode),
                style = MaterialTheme.typography.labelSmall,
                color = if (result.exitCode == 0) colors.textSecondary else colors.danger,
            )
            // The daemon merges stderr into stdout on a failed command but only returns stdout on
            // success: what is on screen is not always "the whole output", and saying so is cheaper
            // than a debugging session about a missing line.
            if (result.exitCode != 0) {
                Text(
                    text = stringResource(R.string.workspace_terminal_flux),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
//  Git
// ═══════════════════════════════════════════════════════════════════════

/**
 * What the git tab is about to do, once the owner confirms it. Kept local to the screen: the
 * pending action is UI state, not something the runtime should ever see.
 */
private class WorkspaceConfirm(
    val title: String,
    val confirm: String,
    val action: () -> Unit,
)

/**
 * Branch, upstream distance, both sides of the index, the patch of a file, a commit box the
 * runtime can fill, push/pull and the log.
 *
 * A refusal from git reaches the banner above verbatim ("nothing to commit", "non-fast-forward"):
 * that text is the only useful description of what went wrong.
 */
@Composable
private fun ColumnScope.GitTab(state: WorkspaceUiState, viewModel: WorkspaceViewModel) {
    val colors = LocalMercuryColors.current
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<WorkspaceConfirm?>(null) }
    val git = state.git

    if (git == null) {
        // A failed request and a folder that is not a repository are two different facts: the
        // first one is ours to admit, the second one is the workspace's.
        QuietLine(
            stringResource(
                if (state.info?.isGit == false) {
                    R.string.workspace_git_aucun_depot
                } else {
                    R.string.workspace_git_indisponible
                },
            ),
        )
        TextButton(onClick = viewModel::refreshGit, enabled = !state.busy) {
            Text(stringResource(R.string.workspace_git_rafraichir))
        }
        return
    }

    KeyValueRow(stringResource(R.string.workspace_git_branche), git.branch.ifBlank { "—" }, highlight = true)
    KeyValueRow(
        stringResource(R.string.workspace_git_amont),
        git.upstream.ifBlank { stringResource(R.string.workspace_git_aucun) },
    )
    KeyValueRow(
        stringResource(R.string.workspace_git_synchronisation),
        GitJson.syncNotices(git.ahead, git.behind).joinToString(" · ") { noticeText(context, it) },
    )

    val staged = state.staged
    val unstaged = state.unstaged

    if (staged.isEmpty() && unstaged.isEmpty()) {
        QuietLine(stringResource(R.string.workspace_git_arbre_propre))
    }

    if (staged.isNotEmpty()) {
        ChangeLabel(pluralStringResource(R.plurals.workspace_git_indexes, staged.size, staged.size))
        staged.forEach { file ->
            GitFileRow(
                file = file,
                staged = true,
                enabled = !state.busy,
                onOpen = { viewModel.showDiff(file.path, true) },
                onToggle = { viewModel.unstage(listOf(file.path)) },
            )
        }
    }
    if (unstaged.isNotEmpty()) {
        ChangeLabel(pluralStringResource(R.plurals.workspace_git_modifies, unstaged.size, unstaged.size))
        unstaged.forEach { file ->
            GitFileRow(
                file = file,
                staged = false,
                enabled = !state.busy,
                onOpen = { viewModel.showDiff(file.path, false) },
                onToggle = { viewModel.stage(listOf(file.path)) },
            )
        }
    }

    IconActionBar(
        listOf(
            QuickAction(
                icon = Icons.Rounded.Add,
                label = stringResource(R.string.workspace_git_indexer_tout),
                enabled = !state.busy && unstaged.isNotEmpty(),
                onClick = viewModel::stageAll,
            ),
            QuickAction(
                icon = Icons.Rounded.Remove,
                label = stringResource(R.string.workspace_git_desindexer_tout),
                enabled = !state.busy && staged.isNotEmpty(),
                onClick = viewModel::unstageAll,
            ),
        ),
    )

    // The patch of the file last tapped — drawn, not interpreted (DiffBlock classifies the lines).
    val patch = state.diff
    if (patch == null) {
        Text(
            text = stringResource(R.string.workspace_git_choisir),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.diffPath.orEmpty(),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        if (state.diffStaged) {
                            R.string.workspace_git_diff_indexe
                        } else {
                            R.string.workspace_git_diff_non_indexe
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
            IconButton(onClick = viewModel::dismissDiff, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.workspace_git_diff_fermer),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (patch.isBlank()) {
            QuietLine(stringResource(R.string.workspace_git_diff_vide))
        } else {
            DiffBlock(text = patch, modifier = Modifier.padding(top = 4.dp))
        }
    }

    OutlinedTextField(
        value = state.commitDraft,
        onValueChange = viewModel::setCommitDraft,
        label = { Text(stringResource(R.string.workspace_git_message)) },
        minLines = 2,
        enabled = !state.busy,
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
    )

    // Resolved here: the onClick lambdas are not composable.
    val commitTitle = stringResource(R.string.workspace_git_committer)
    val commitConfirm = stringResource(R.string.workspace_confirmer)
    val pushTitle = stringResource(R.string.workspace_git_pousser)
    val pullTitle = stringResource(R.string.workspace_git_tirer)
    IconActionBar(
        listOf(
            QuickAction(
                icon = Icons.Rounded.AutoAwesome,
                label = stringResource(R.string.workspace_git_generer),
                enabled = !state.busy,
                onClick = viewModel::generateCommitMessage,
            ),
            QuickAction(
                icon = Icons.Rounded.Check,
                label = stringResource(R.string.workspace_git_committer),
                enabled = !state.busy && state.commitDraft.isNotBlank() && staged.isNotEmpty(),
                onClick = {
                    confirm = WorkspaceConfirm(commitTitle, commitConfirm, viewModel::commit)
                },
            ),
            QuickAction(
                icon = Icons.Rounded.CloudUpload,
                label = stringResource(R.string.workspace_git_pousser),
                enabled = !state.busy,
                onClick = {
                    confirm = WorkspaceConfirm(pushTitle, commitConfirm, viewModel::push)
                },
            ),
            QuickAction(
                icon = Icons.Rounded.CloudDownload,
                label = stringResource(R.string.workspace_git_tirer),
                enabled = !state.busy,
                onClick = {
                    confirm = WorkspaceConfirm(pullTitle, commitConfirm, viewModel::pull)
                },
            ),
            QuickAction(
                icon = Icons.Rounded.Refresh,
                label = stringResource(R.string.workspace_git_rafraichir),
                enabled = !state.busy,
                onClick = viewModel::refreshGit,
            ),
        ),
    )

    SectionTitle(stringResource(R.string.workspace_git_commits))
    if (state.gitLog.isEmpty()) {
        QuietLine(stringResource(R.string.workspace_git_aucun_commit))
    }
    state.gitLog.forEach { commit ->
        ActionRow(
            title = commit.message.ifBlank { commit.short },
            subtitle = listOfNotNull(
                commit.short.ifBlank { null },
                commit.date.ifBlank { null },
            ).joinToString(" · "),
        )
    }

    confirm?.let { pending ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = colors.surface,
            title = {
                Text(
                    stringResource(R.string.workspace_confirmer_titre, pending.title),
                    color = colors.textPrimary,
                )
            },
            text = {
                Text(
                    stringResource(R.string.workspace_confirmer_corps),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    pending.action()
                }) { Text(pending.confirm, color = colors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) {
                    Text(stringResource(R.string.workspace_annuler), color = colors.textSecondary)
                }
            },
        )
    }
}

/** One changed path: the patch opens on a tap, the glyph moves it across the index. */
@Composable
private fun GitFileRow(
    file: GitFileDto,
    staged: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    val colors = LocalMercuryColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(enabled = enabled, onClick = onOpen)
            .padding(start = 10.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.path,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = noticeText(GitJson.statusNotice(file.status)),
                style = MaterialTheme.typography.labelSmall,
                color = if (staged) colors.accent else colors.textSecondary,
                maxLines = 1,
            )
        }
        IconButton(onClick = onToggle, enabled = enabled, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (staged) Icons.Rounded.Remove else Icons.Rounded.Add,
                contentDescription = stringResource(
                    if (staged) R.string.workspace_git_desindexer else R.string.workspace_git_indexer,
                ),
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ChangeLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = LocalMercuryColors.current.textSecondary,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp, start = 4.dp),
    )
}

@Composable
private fun QuietLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = LocalMercuryColors.current.textSecondary,
        modifier = Modifier.padding(top = 8.dp),
    )
}
