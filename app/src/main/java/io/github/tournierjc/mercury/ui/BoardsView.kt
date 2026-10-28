package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerOff
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.ViewKanban
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.data.BoardJson
import io.github.tournierjc.mercury.data.BoardMetrics
import io.github.tournierjc.mercury.data.BoardSpend
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import io.github.tournierjc.mercury.ui.theme.MercuryColors
import io.github.tournierjc.mercury.util.formatCost
import io.github.tournierjc.mercury.util.formatTokens
import io.github.tournierjc.mercury.vm.BoardsUiState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Composable
internal fun BoardList(
    state: BoardsUiState,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCreate: () -> Unit,
    onRetry: () -> Unit,
    onSetActive: (String, Boolean) -> Unit,
) {
    val colors = LocalMercuryColors.current
    if (state.available == false) {
        // A service that did not start is not an empty list: saying "aucun plateau" would tell
        // the owner his task queue is clear when it is merely unreadable.
        Text(
            text = stringResource(R.string.boards_indisponible_titre),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textPrimary,
        )
        Text(
            text = stringResource(R.string.boards_indisponible_corps),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
        IconActionBar(
            listOf(
                QuickAction(
                    icon = Icons.Rounded.Refresh,
                    label = stringResource(R.string.boards_indisponible_reessayer),
                    tint = colors.accent,
                    onClick = onRetry,
                ),
            ),
        )
        return
    }

    // The web app puts its one primary verb in the header, and so does this screen: a board list
    // whose only way forward was a "Nouveau plateau" row at the bottom read as a dead end.
    Button(
        onClick = onCreate,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        shape = MaterialTheme.shapes.small,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            text = stringResource(R.string.boards_nouveau_plateau),
            modifier = Modifier.padding(start = 8.dp),
        )
    }

    if (state.boards.isEmpty()) {
        Text(
            text = if (state.loading) {
                stringResource(R.string.common_loading)
            } else {
                stringResource(R.string.boards_aucun_plateau)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        return
    }

    state.boards.forEach { board ->
        BoardCard(
            board = board,
            onOpen = { BoardJson.id(board).takeIf { it.isNotBlank() }?.let(onOpen) },
            onToggleActive = { active ->
                BoardJson.id(board).takeIf { it.isNotBlank() }?.let { onSetActive(it, active) }
            },
            onDelete = { BoardJson.id(board).takeIf { it.isNotBlank() }?.let(onDelete) },
        )
    }
}

/**
 * One board, as the web app draws it: name, description, an Active pill you can tap, how many cards,
 * and how old the board is.
 *
 * The phone used to say "ouvrir" on the right and print `actif` in a subtitle — a state you can only
 * read, and a verb that repeats the tap you already made by touching the row.
 */
@Composable
internal fun BoardCard(
    board: JsonObject,
    onOpen: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalMercuryColors.current
    val active = BoardJson.boolField(board, "active") == true
    val description = BoardJson.description(board).trim()
    val cards = BoardJson.intField(board, "cardCount", "totalCards")
        ?: BoardJson.cards(board).size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(MaterialTheme.shapes.small)
            .background(colors.surface)
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = BoardJson.title(board),
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ActivePill(active = active, onClick = { onToggleActive(!active) })
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.boards_supprimer),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        if (description.isNotBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(
                text = pluralStringResource(R.plurals.boards_nb_cartes, cards, cards),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
            BoardAge(board)
        }
    }
}

/** The web app's `Active` badge, as a control: it says the state and takes the tap that changes it. */
@Composable
private fun ActivePill(active: Boolean, onClick: () -> Unit) {
    val colors = LocalMercuryColors.current
    val tint = if (active) colors.accent else colors.textSecondary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(end = 6.dp)
            .clip(RoundedCornerShape(50))
            .background(if (active) colors.accent.copy(alpha = 0.16f) else colors.surfaceMuted)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Icon(
            if (active) Icons.Rounded.PowerSettingsNew else Icons.Rounded.PowerOff,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = stringResource(if (active) R.string.boards_actif else R.string.boards_inactif),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/**
 * The four counters the web app prints above a board, then the tokens and the estimated cost.
 *
 * Counted from the cards the phone already holds: the same numbers, no second source to disagree
 * with the stream. A board with no cards says so once instead of printing four zeros.
 */
@Composable
internal fun BoardMetricsStrip(metrics: BoardMetrics) {
    val colors = LocalMercuryColors.current
    if (metrics.empty) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        Metric(stringResource(R.string.boards_metric_running, metrics.running), colors.accent)
        Metric(stringResource(R.string.boards_metric_pending, metrics.pending), colors.textSecondary)
        Metric(stringResource(R.string.boards_metric_done, metrics.done), colors.success)
        Metric(stringResource(R.string.boards_metric_failed, metrics.failed), colors.danger)
        Text(
            text = buildString {
                append("${formatTokens(metrics.tokens)} tokens")
                if (metrics.cost > 0.0) append(" · ${formatCost(metrics.cost)}")
            },
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun Metric(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.padding(end = 10.dp),
    )
}

/**
 * The board's verbs, as the web app lays them out: one primary action in reach, the rest behind a
 * menu.
 *
 * Seven glyphs with words under them used to fill a whole collapsible section on every board, and
 * the six rarely used ones cost the cards above the fold. The order of the menu is the web app's.
 */
@Composable
internal fun BoardToolbar(
    state: BoardsUiState,
    onSetActive: (Boolean) -> Unit,
    onRunAll: () -> Unit,
    onHaltAll: () -> Unit,
    onPlan: () -> Unit,
    onContext: () -> Unit,
    onClearDone: () -> Unit,
    onGenerate: () -> Unit,
    onNewCard: () -> Unit,
) {
    val colors = LocalMercuryColors.current
    val active = BoardJson.boolField(state.board, "active") == true
    var menu by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    ) {
        Button(
            onClick = onRunAll,
            enabled = !state.busy,
            shape = MaterialTheme.shapes.small,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
            Text(
                text = stringResource(R.string.boards_tout_lancer),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Text(
            text = stringResource(if (active) R.string.boards_actif else R.string.boards_inactif),
            style = MaterialTheme.typography.labelSmall,
            color = if (active) colors.accent else colors.textSecondary,
            modifier = Modifier.padding(start = 10.dp),
        )
        Spacer(Modifier.weight(1f))
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.boards_menu_actions),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                MenuItem(Icons.Rounded.Add, R.string.boards_nouvelle_carte) { menu = false; onNewCard() }
                MenuItem(Icons.Rounded.AutoAwesome, R.string.boards_generer) { menu = false; onGenerate() }
                MenuItem(Icons.Rounded.ViewKanban, R.string.boards_plan) { menu = false; onPlan() }
                MenuItem(Icons.Rounded.InsertDriveFile, R.string.boards_contexte) { menu = false; onContext() }
                MenuItem(Icons.Rounded.CleaningServices, R.string.boards_nettoyer) { menu = false; onClearDone() }
                MenuItem(
                    if (active) Icons.Rounded.PowerOff else Icons.Rounded.PowerSettingsNew,
                    if (active) R.string.boards_desactiver else R.string.boards_activer,
                ) { menu = false; onSetActive(!active) }
                MenuItem(Icons.Rounded.Stop, R.string.boards_tout_arreter, danger = true) { menu = false; onHaltAll() }
            }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, label: Int, danger: Boolean = false, onClick: () -> Unit) {
    val colors = LocalMercuryColors.current
    DropdownMenuItem(
        text = {
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.bodyMedium,
                color = if (danger) colors.danger else colors.textPrimary,
            )
        },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                tint = if (danger) colors.danger else colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        },
        onClick = onClick,
    )
}

/**
 * The cards of one status, folded away — the web app's own phone layout, and for its reason: four
 * open lanes put the running work below the fold on a phone, which is the one thing you came to see.
 *
 * Open by default are the two lanes that ask something of the owner: what runs, and what waits.
 */
@Composable
internal fun StatusLane(
    status: String,
    cards: List<JsonObject>,
    onRun: (String) -> Unit,
    onHalt: (String) -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val colors = LocalMercuryColors.current
    val tint = statusTint(status, colors)
    CollapsibleSection(
        title = "${statusLabel(status)} · ${cards.size}",
        // What runs and what waits on the owner opens itself; the rest is history, folded.
        defaultOpen = status == "running" || status == "pending" || status == "paused" || status == "question",
        stateKey = "board-lane-$status",
    ) {
        if (cards.isEmpty()) {
            Text(
                text = stringResource(R.string.boards_lane_vide),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        cards.forEach { card ->
            BoardCardRow(
                card = card,
                tint = tint,
                onRun = { BoardJson.id(card).takeIf { it.isNotBlank() }?.let(onRun) },
                onHalt = { BoardJson.id(card).takeIf { it.isNotBlank() }?.let(onHalt) },
                onOpen = { BoardJson.id(card).takeIf { it.isNotBlank() }?.let(onOpen) },
                onDelete = { BoardJson.id(card).takeIf { it.isNotBlank() }?.let(onDelete) },
            )
        }
    }
}

/**
 * One card: the status as a coloured edge, the priority, the task, then what it cost and how long
 * ago — and the one verb that makes sense in that status, not four that mostly do not.
 */
@Composable
internal fun BoardCardRow(
    card: JsonObject,
    tint: Color,
    onRun: () -> Unit,
    onHalt: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalMercuryColors.current
    val status = BoardJson.status(card)
    val spend = BoardSpend.of(card)
    val error = (card["error"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    val progress = (card["progress"] as? JsonPrimitive)?.contentOrNull.orEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(MaterialTheme.shapes.small)
            .background(colors.surface)
            .clickable(onClick = onOpen),
    ) {
        // The left edge: the web app's card border, and the only colour that says the status.
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(IntrinsicSize.Min)
                .background(tint),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 11.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PriorityChip(BoardJson.priority(card))
                val steps = BoardJson.labels(card).size
                if (steps > 0) {
                    Text(
                        text = stringResource(R.string.boards_etapes, steps),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            Text(
                text = BoardJson.title(card),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (status == "running" && progress.isNotBlank()) {
                Text(
                    text = progress,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (status == "failed" && error.isNotBlank()) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.danger,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (spend != null) {
                Text(
                    text = buildString {
                        append("${formatTokens(spend.total)} tokens")
                        if (spend.input > 0 || spend.output > 0) append(" · ${formatCost(BoardSpend.cost(spend))}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        // The verb of that status, in a column of its own: the gesture the owner came to make.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(end = 4.dp, top = 4.dp),
        ) {
            when (status) {
                "running" -> CardVerb(Icons.Rounded.Stop, R.string.boards_arreter, colors.danger, onHalt)
                "pending", "paused" -> CardVerb(Icons.Rounded.PlayArrow, R.string.boards_lancer, colors.accent, onRun)
                "failed" -> CardVerb(Icons.Rounded.Refresh, R.string.boards_reessayer, colors.danger, onRun)
                "question" -> CardVerb(Icons.Rounded.Forum, R.string.boards_action_details, colors.attention, onOpen)
                "done" -> CardVerb(Icons.Rounded.Check, R.string.boards_action_details, colors.success, onOpen)
                else -> CardVerb(Icons.Rounded.ChevronRight, R.string.boards_action_details, colors.textSecondary, onOpen)
            }
        }
    }
}

@Composable
private fun CardVerb(icon: ImageVector, label: Int, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
        Icon(icon, contentDescription = stringResource(label), tint = tint, modifier = Modifier.size(18.dp))
    }
}

/** LOW / MED / HIGH / CRIT, in the web app's words and colours. */
@Composable
private fun PriorityChip(priority: String) {
    val colors = LocalMercuryColors.current
    val (label, tint) = when (priority.lowercase()) {
        "low" -> R.string.boards_prio_basse to colors.textSecondary
        "high" -> R.string.boards_prio_haute to colors.warning
        "critical" -> R.string.boards_prio_critique to colors.danger
        "medium", "normal" -> R.string.boards_prio_moyenne to colors.warning
        else -> return
    }
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.labelSmall,
        color = tint,
        modifier = Modifier
            .border(1.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/**
 * The web app's purple banner above the columns: cards waiting for *your* answer, not for the next
 * slot. On a phone it is the difference between "the board is working" and "the board is stuck on me".
 */
@Composable
internal fun AttentionBanner(count: Int) {
    val colors = LocalMercuryColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(MaterialTheme.shapes.small)
            .background(colors.attention.copy(alpha = 0.14f))
            .border(1.dp, colors.attention.copy(alpha = 0.4f), MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(colors.attention),
        )
        Text(
            text = pluralStringResource(R.plurals.boards_attente_reponse, count, count),
            style = MaterialTheme.typography.labelMedium,
            color = colors.attention,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** The colour a status wears, one place, so the lane, the edge and the verb agree. */
private fun statusTint(status: String, colors: MercuryColors): Color = when (status) {
    "running" -> colors.accent
    "done" -> colors.success
    "failed" -> colors.danger
    "question" -> colors.attention
    else -> colors.hairlineStrong
}

/** When the board was made, in the words the app already uses for an age. */
@Composable
private fun BoardAge(board: JsonObject) {
    val colors = LocalMercuryColors.current
    val created = BoardJson.longField(board, "createdAt") ?: return
    val millis = if (created < 1_000_000_000_000L) created * 1000 else created
    val minutes = ((System.currentTimeMillis() - millis) / 60_000).coerceAtLeast(0)
    val text = when {
        minutes < 60 -> stringResource(R.string.notice_age_minutes, minutes.toString())
        minutes < 24 * 60 -> stringResource(R.string.notice_age_hours, (minutes / 60).toString())
        else -> stringResource(R.string.notice_age_days, (minutes / (24 * 60)).toString())
    }
    Text(
        text = " · $text",
        style = MaterialTheme.typography.labelSmall,
        color = colors.textSecondary,
        maxLines = 1,
    )
}

/** The six statuses both clients share, in the words the phone already uses (SPECS/111 §1). */
@Composable
internal fun statusLabel(status: String): String = when (status) {
    "pending" -> stringResource(R.string.boards_statut_pending)
    "paused" -> stringResource(R.string.boards_statut_paused)
    "question" -> stringResource(R.string.boards_statut_question)
    "running" -> stringResource(R.string.boards_statut_running)
    "done" -> stringResource(R.string.boards_statut_done)
    "failed" -> stringResource(R.string.boards_statut_failed)
    else -> status
}
