package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Shared building blocks for the secondary screens (settings, console). */

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    onRefresh: (() -> Unit)? = null,
    /** Before the title — a bot's face, when the screen is about someone. */
    leading: (@Composable () -> Unit)? = null,
    /** Extra trailing actions — a "+", a menu — drawn after the refresh button. */
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    val colors = LocalMercuryColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = colors.textPrimary)
        }
        if (leading != null) {
            Box(modifier = Modifier.padding(start = 2.dp, end = 8.dp)) { leading() }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onRefresh != null) {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.common_refresh), tint = colors.textSecondary)
            }
        }
        actions()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = LocalMercuryColors.current.textSecondary,
        modifier = modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp),
    )
}

/**
 * A titled block that folds away.
 *
 * The bot sheet grew to a dozen sections — persona, permissions, model, budget, fleet, tasks,
 * mailbox, journal, deletion — and every one of them is a wall of text on a phone. Collapsed by
 * default, the sheet becomes a list of names you can scan and open one at a time; the two the
 * screen exists for (the summary and "write to it") are passed `defaultOpen = true`.
 *
 * `rememberSaveable` keeps one section's state across a rotation without tying it to a sibling's.
 * The key is `stateKey` when given, the title otherwise — so a heading whose text moves with its
 * own count (`bots_section_waiting`, 3 → 4) must pass a stable `stateKey`, or the section folds
 * and unfolds every time that number changes.
 */
@Composable
fun CollapsibleSection(
    title: String,
    defaultOpen: Boolean = false,
    /** Stable save key for a heading whose text carries a count; the title is the key otherwise. */
    stateKey: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalMercuryColors.current
    var open by rememberSaveable(stateKey ?: title) { mutableStateOf(defaultOpen) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable { open = !open }
            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = if (open) stringResource(R.string.common_collapse_section, title) else stringResource(R.string.common_expand_section, title),
            tint = colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
    // Flat, like the conversation: a label and hairline-separated rows, no card around every
    // block. Twelve rounded panels stacked on a phone read as a settings dump.
    if (open) {
        // 10 dp of air under the header: the title is a label, not a lead-in glued to the text.
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp),
            content = content,
        )
        HorizontalDivider(color = colors.hairline)
    }
}

/**
 * One round icon button with a one-word label — the vocabulary the conversation screens use.
 *
 * Text buttons in a row ("Démarrer · Arrêter · Exécuter · Désactiver · Supprimer") are five
 * identical grey words; a glyph with a label underneath is read at a glance, and the label is
 * still there for anyone who does not know the icon.
 */
data class QuickAction(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val tint: androidx.compose.ui.graphics.Color? = null,
    val enabled: Boolean = true,
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun IconActionBar(
    actions: List<QuickAction>,
    modifier: Modifier = Modifier,
    /** Centred when the bar is the section's only control: a lone button hugs the left edge. */
    centered: Boolean = false,
) {
    val colors = LocalMercuryColors.current
    // Wrapping, not scrolling: seven actions on a phone were cut mid-label at the screen edge
    // ("Désact…"), which reads as a rendering bug rather than as a list that continues.
    val container = @Composable { content: @Composable () -> Unit ->
        if (centered) {
            Row(
                modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) { content() }
        } else {
            FlowRow(
                modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                // Four per line, always: a row of seven either overflows or crowds, and the
                // break lands in the same place on every phone width.
                maxItemsInEachRow = 4,
            ) { content() }
        }
    }
    container {
        actions.forEach { action ->
            val tint = action.tint ?: colors.textPrimary
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(72.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(enabled = action.enabled) { action.onClick() }
                    .padding(vertical = 6.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            colors.surface,
                            MaterialTheme.shapes.extraSmall,
                        ),
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = action.label,
                        tint = if (action.enabled) tint else colors.textSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(21.dp),
                    )
                }
                Text(
                    text = action.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (action.enabled) colors.textSecondary else colors.textSecondary.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
    }
}


@Composable
fun KeyValueRow(label: String, value: String, highlight: Boolean = false) {
    val colors = LocalMercuryColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (highlight) colors.accent else colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Tappable row with an optional trailing label; used for lists and pickers. */
@Composable
fun ActionRow(
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    /** Verbs for the row, drawn as a glyph column on the right — the beliefs/memories shape. */
    actions: List<QuickAction> = emptyList(),
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalMercuryColors.current
    // One card shape per screen: text breathes on the left, verbs live in a column on the right.
    // A row of text buttons under the last line reads as a footnote, and a second list drawn the
    // other way makes the screen look like two designs.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .then(if (onClick != null && enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = when {
                        !enabled -> colors.textSecondary.copy(alpha = 0.5f)
                        selected -> colors.accent
                        else -> colors.textPrimary
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (trailing != null) {
                    Text(
                        text = trailing,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                }
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (actions.isNotEmpty()) {
            // Two per row, not one: four verbs stacked vertically made a one-line card 152 dp
            // tall. The label of each glyph is its content description — a list row carries
            // glyphs (like a belief), while a bar of actions carries the word underneath.
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = 6.dp),
            ) {
                actions.chunked(2).forEach { pair ->
                    Row {
                        pair.forEach { action ->
                            IconButton(
                                onClick = action.onClick,
                                enabled = action.enabled,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = action.icon,
                                    contentDescription = action.label,
                                    tint = action.tint ?: colors.textSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProgressLine(ratio: Float) {
    val colors = LocalMercuryColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(colors.surfaceMuted),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(ratio.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(colors.accent),
        )
    }
}

@Composable
fun InlineBanner(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit,
    /** Overrides the two-state colour when the caller knows a severity (see `NoticeBanner`). */
    tint: Color? = null,
) {
    val colors = LocalMercuryColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(colors.surface)
            .clickable { onDismiss() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = tint ?: if (isError) colors.danger else colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.common_ok),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
    }
}

/**
 * Best-effort one-line summary for payloads Mercury does not schema for us
 * (bots, tasks, schedules, agents). Falls back to the first scalar field.
 */
fun JsonObject.titleHint(): String {
    val preferred = listOf("title", "name", "id", "label", "prompt", "command", "cron")
    for (key in preferred) {
        val value = (this[key] as? JsonPrimitive)?.contentOrNull
        if (!value.isNullOrBlank()) return value
    }
    return entries.firstOrNull { it.value is JsonPrimitive }?.value?.let { (it as JsonPrimitive).contentOrNull }.orEmpty()
}

/** Best-effort status/time line for the same payloads. */
fun JsonObject.detailHint(): String {
    val parts = mutableListOf<String>()
    for (key in listOf("status", "state", "enabled", "schedule", "cron", "nextRun", "startedAt")) {
        val value = (this[key] as? JsonPrimitive)?.contentOrNull
        if (!value.isNullOrBlank()) parts += "$key=$value"
    }
    return parts.joinToString(" · ")
}

