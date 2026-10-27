package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour", tint = colors.textPrimary)
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
                Icon(Icons.Rounded.Refresh, contentDescription = "Rafraîchir", tint = colors.textSecondary)
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

@Composable
fun CardBlock(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalMercuryColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        content = content,
    )
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
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalMercuryColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(if (onClick != null && enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
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
fun InlineBanner(message: String, isError: Boolean, onDismiss: () -> Unit) {
    val colors = LocalMercuryColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .clickable { onDismiss() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) colors.danger else colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "OK",
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

/** Row of compact text actions under a list entry (start/stop/delete…). */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ButtonRow(content: @Composable () -> Unit) {
    // FlowRow, not Row: four actions in a card overflowed on a phone and the last one
    // ("Désactiver") was pushed off the screen edge instead of wrapping.
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .background(LocalMercuryColors.current.surfaceMuted, RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.Center,
    ) { content() }
}
