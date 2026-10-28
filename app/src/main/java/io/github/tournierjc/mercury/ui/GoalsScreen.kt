package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tournierjc.mercury.MercuryApp
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.vm.GoalTab
import io.github.tournierjc.mercury.vm.GoalsViewModel

/**
 * What the agent is trying to do, and what it is building.
 *
 * The webapp has two entries, goals and projects, and both are memories of a given type — so this
 * is one screen with two tabs, reading the memory route twice rather than pretending to have a
 * collection of its own. Adding and deleting work from here; the wording of a goal is edited in
 * the webapp, which is said once, at the bottom, instead of being discovered by a missing button.
 */
@Composable
fun GoalsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MercuryApp).container }
    val viewModel: GoalsViewModel = viewModel(factory = GoalsViewModel.factory(container))
    val state by viewModel.state.collectAsState()
    val colors = LocalMercuryColors.current

    var showAdd by remember { mutableStateOf(false) }
    val shown = state.shown

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .safeDrawingPadding(),
    ) {
        ScreenHeader(
            title = stringResource(R.string.brain_objectifs_projets),
            subtitle = when (state.tab) {
                GoalTab.Goals -> pluralStringResource(R.plurals.brain_nb_objectifs, shown.size, shown.size)
                GoalTab.Projects -> pluralStringResource(R.plurals.brain_nb_projets, shown.size, shown.size)
            },
            onBack = onBack,
            onRefresh = viewModel::refresh,
        )
        state.message?.let { notice -> NoticeBanner(notice, onDismiss = viewModel::clearBanner) }
        state.error?.let { notice -> NoticeBanner(notice, onDismiss = viewModel::clearBanner) }

        SegmentedTabs(
            labels = GoalTab.entries.map {
                when (it) {
                    GoalTab.Goals -> stringResource(R.string.brain_objectifs)
                    GoalTab.Projects -> stringResource(R.string.brain_projets)
                }
            },
            selected = state.tab.ordinal,
            onSelect = { index -> viewModel.selectTab(GoalTab.entries[index]) },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            CollapsibleSection(
                title = when (state.tab) {
                    GoalTab.Goals -> stringResource(R.string.brain_objectifs)
                    GoalTab.Projects -> stringResource(R.string.brain_projets)
                },
                defaultOpen = true,
            ) {
                if (shown.isEmpty()) {
                    Text(
                        text = when {
                            state.loading -> stringResource(R.string.common_loading)
                            state.tab == GoalTab.Goals -> stringResource(R.string.brain_objectif_vide)
                            else -> stringResource(R.string.brain_projet_vide)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
                shown.forEach { memory ->
                    MemoryRow(
                        memory = memory,
                        deleteLabel = stringResource(R.string.brain_supprimer),
                        onForget = { viewModel.forget(memory.id) },
                    )
                }
                IconActionBar(
                    listOf(
                        QuickAction(
                            icon = Icons.Rounded.Add,
                            label = stringResource(R.string.memory_ajouter),
                            tint = colors.accent,
                            onClick = { showAdd = true },
                        ),
                    ),
                )
            }
            Text(
                text = stringResource(R.string.brain_modifier_webapp),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
            )
        }
    }

    if (showAdd) {
        AddGoalDialog(
            tab = state.tab,
            onDismiss = { showAdd = false },
            onConfirm = { summary, detail ->
                showAdd = false
                viewModel.add(summary, detail)
            },
        )
    }
}


/**
 * A goal or a project, written from the phone. The type is the open tab's, so the dialog asks for
 * the two things that cannot be guessed: what it is, and the sentence behind it.
 */
@Composable
private fun AddGoalDialog(
    tab: GoalTab,
    onDismiss: () -> Unit,
    onConfirm: (summary: String, detail: String) -> Unit,
) {
    var summary by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    val colors = LocalMercuryColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text(
                text = when (tab) {
                    GoalTab.Goals -> stringResource(R.string.brain_objectif_nouveau)
                    GoalTab.Projects -> stringResource(R.string.brain_projet_nouveau)
                },
                color = colors.textPrimary,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = { Text(stringResource(R.string.memory_resume)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    label = { Text(stringResource(R.string.memory_detail_optionnel)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(summary, detail) }, enabled = summary.isNotBlank()) {
                Text(stringResource(R.string.memory_ajouter))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.memory_annuler)) } },
    )
}
