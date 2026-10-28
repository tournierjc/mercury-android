package io.github.tournierjc.mercury.ui

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
                        state.loading -> stringResource(R.string.memory_chargement)
                        !state.available -> stringResource(R.string.brain_personnes_indisponible)
                        else -> stringResource(R.string.memory_aucune_personne)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                )
            }
            state.persons.forEach { person -> PersonRow(person) }
            Text(
                text = " ",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

/** One person, in the same card shape the memories use: the name, the relation, the sentence. */
@Composable
private fun PersonRow(person: PersonUi) {
    val colors = LocalMercuryColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(colors.surfaceMuted, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column {
            person.relation.takeIf { it.isNotBlank() }?.let { relation ->
                Text(
                    text = relation.uppercase(),
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
            person.summary.takeIf { it.isNotBlank() }?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 4,
                )
            }
        }
    }
}
