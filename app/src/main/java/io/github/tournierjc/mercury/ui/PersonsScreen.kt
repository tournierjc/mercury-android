package io.github.tournierjc.mercury.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import io.github.tournierjc.mercury.vm.PersonDetailUi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tournierjc.mercury.MercuryApp
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.vm.PersonUi
import io.github.tournierjc.mercury.vm.PersonsViewModel

/**
 * The people the brain has met, and what it tied to them.
 *
 * A person is a memory the brain filed under someone's name: who they are, what they are to the
 * owner, and the sentence that made it stick. The screen is a list because that is what the
 * question needs — « who does it know? » — with a search field for a brain that has met many.
 */
@Composable
fun PersonsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MercuryApp).container }
    val viewModel: PersonsViewModel = viewModel(factory = PersonsViewModel.factory(container))
    val state by viewModel.state.collectAsState()

    // The unfold is a step of its own: Back folds the person before it leaves the screen.
    val unfolded = state.expanded.isNotBlank()
    BackHandler(enabled = unfolded) {
        state.persons.firstOrNull { it.id == state.expanded }?.let { viewModel.toggle(it) }
    }
    val colors = LocalMercuryColors.current

    Column(
        modifier = Modifier
             .fillMaxSize()
             .background(colors.canvas)
             .safeDrawingPadding(),
    ) {
        ScreenHeader(
            title = stringResource(R.string.memory_personnes),
            subtitle = if (state.persons.isEmpty()) {
                null
            } else {
                val count = maxOf(state.total, state.persons.size)
                pluralStringResource(R.plurals.brain_nb_personnes, count, count)
            },
            onBack = onBack,
            onRefresh = viewModel::refresh,
            refreshing = state.loading,
        )
        state.error?.let { notice -> NoticeBanner(notice, onDismiss = viewModel::clearBanner) }

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::search,
            label = { Text(stringResource(R.string.brain_personnes_rechercher)) },
            singleLine = true,
            modifier = Modifier
                 .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )

        Column(
            modifier = Modifier
                 .weight(1f)
                 .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            if (state.persons.isEmpty()) {
                Text(
                    // An instance with no people route is not an instance with no people: the
                    // two empty screens must not say the same thing.
                    text = when {
                        state.loading -> stringResource(R.string.common_loading)
                        !state.available -> stringResource(R.string.brain_personnes_indisponible)
                        else -> stringResource(R.string.memory_aucune_personne)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                )
            }
            state.persons.forEach { person ->
                PersonRow(
                    person = person,
                    expanded = state.expanded == person.id,
                    detail = state.detail.takeIf { state.expanded == person.id },
                    loading = state.detailLoading && state.expanded == person.id,
                    failed = state.detailFailed && state.expanded == person.id,
                    onToggle = { viewModel.toggle(person) },
                )
            }
            Text(
                text = " ",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

/**
 * One person, in the same card shape the memories use: the name, the relation, the sentence.
 *
 * The webapp pushes a page for a person; on a phone the list keeps the row in place and unfolds it,
 * so the answer stays next to the question and going back does not cost a navigation.
 */
@Composable
private fun PersonRow(
    person: PersonUi,
    expanded: Boolean,
    detail: PersonDetailUi?,
    loading: Boolean,
    failed: Boolean,
    onToggle: () -> Unit,
) {
    val colors = LocalMercuryColors.current
    Column(
        modifier = Modifier
             .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(colors.surfaceMuted, MaterialTheme.shapes.extraSmall)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics {
                contentDescription = person.name
                role = Role.Button
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            person.relation.takeIf { it.isNotBlank() }?.let { relation ->
                Text(
                    text = rawLabel(relation),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.accent,
                )
            }
            Text(
                text = person.name,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                modifier = Modifier.padding(top = if (person.relation.isBlank()) 0.dp else 4.dp),
            )
            // The row keeps its summary until the unfolded body carries a fuller one, so the
            // sentence does not disappear while the request is out.
            person.summary.takeIf { it.isNotBlank() && (detail == null || detail.summary.isBlank()) }?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 4,
                )
            }
        }
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(
                    if (expanded) R.string.brain_personne_replier else R.string.brain_personne_deplier,
                ),
                tint = colors.textSecondary,
                modifier = Modifier
                    .padding(start = 8.dp)
                     .size(20.dp)
                    .rotate(if (expanded) 180f else 0f),
            )
        }

        if (expanded) {
            PersonDetail(detail = detail, loading = loading, failed = failed)
        }
    }
}

/** What the row unfolds into: the traits, then the memories, or the reason there are none. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonDetail(detail: PersonDetailUi?, loading: Boolean, failed: Boolean) {
    val colors = LocalMercuryColors.current
    when {
        loading -> Text(
            text = stringResource(R.string.common_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 10.dp),
        )

        failed -> Text(
            text = stringResource(R.string.brain_personne_detail_echoue),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 10.dp),
        )

        detail == null -> Unit

        else -> Column(modifier = Modifier.padding(top = 10.dp)) {
            // The row hides its own summary when unfolded, so the full text lives here — the list
            // version was capped at four lines and the page version is not.
            detail.summary.takeIf { it.isNotBlank() }?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
            }

            detail.traits.takeIf { it.isNotEmpty() }?.let { traits ->
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    traits.forEach { trait ->
                        Text(
                            text = trait,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textPrimary,
                            modifier = Modifier
                                .border(1.dp, colors.hairlineStrong, MaterialTheme.shapes.extraSmall)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                 Spacer(Modifier.height(10.dp))
            }

            Text(
                text = pluralStringResource(R.plurals.brain_personne_memoires, detail.total, detail.total),
                style = MaterialTheme.typography.labelSmall,
                color = colors.accent,
            )
            if (detail.memories.isEmpty()) {
                Text(
                    text = stringResource(R.string.brain_personne_aucun_souvenir),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            detail.memories.forEach { memory ->
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = rawLabel(memory.type),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                    Text(
                        text = memory.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    formatWhen(memory.createdAt)?.let { when_ ->
                        Text(
                            text = when_,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
