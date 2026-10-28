package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tournierjc.mercury.MercuryApp
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.vm.ConsoleViewModel
import io.github.tournierjc.mercury.R

/**
 * The fleet's deliverables, as a destination of their own.
 *
 * They used to live at the bottom of the Console, reachable only by scrolling past the
 * runtime, the providers, the agents and the git card. A tapped notification now lands here,
 * and so does the drawer entry — the list is the same code the Console section renders.
 */
@Composable
fun DeliverablesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MercuryApp).container }
    val viewModel: ConsoleViewModel = viewModel(factory = ConsoleViewModel.factory(container))
    val state by viewModel.state.collectAsState()
    val avatars by viewModel.avatars.collectAsState()
    val colors = LocalMercuryColors.current

    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        ScreenHeader(
            title = stringResource(R.string.out_title),
            subtitle = when (state.outputs.size) {
                0 -> stringResource(R.string.out_subtitle)
                1 -> stringResource(R.string.out_one_file)
                else -> stringResource(R.string.out_n_files, state.outputs.size)
            },
            onBack = onBack,
            onRefresh = viewModel::refresh,
            refreshing = state.loading,
        )
        FleetDeliverablesSection(state, viewModel, avatars)
        // Same banner as every other screen: the error is dismissable, not a permanent stray line.
        state.error?.let { notice -> NoticeBanner(notice, onDismiss = viewModel::clearBanner) }
    }

    OutputPreviewHost(state, viewModel)
}
