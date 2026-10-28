package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors

/**
 * Every control in a "type and send" strip is this size, so the row reads as one line and not as
 * three. Shared with the bot sheet's quick task, which had its own 36 dp buttons beside a 44 dp
 * field and a 6 dp nudge that left the words below the glyphs.
 */
internal val ComposerControlSize = 40.dp
internal val ComposerControlIcon = 20.dp

/** The width of the two controls that open the row: 30 dp leaves 10 dp between two glyphs. */
private val LeadingControlWidth = 30.dp

@Composable
fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    /** Null hides the microphone: a screen that cannot dictate should not offer it. */
    onVoice: (() -> Unit)?,
    /** Null hides the paperclip, same reason. */
    onAttach: (() -> Unit)?,
    attachmentCount: Int,
    busy: Boolean,
    /** What the empty field invites, in the words of the current conversation. */
    hint: String = stringResource(R.string.composer_hint_default),
    modifier: Modifier = Modifier,
) {
    val colors = LocalMercuryColors.current
    val focus = LocalFocusManager.current
    val field = remember { FocusRequester() }
    var showCommands by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(colors.surface)
            // Same hairline as a rounded field: without it the composer melts into the canvas.
            .border(1.dp, colors.hairlineStrong, MaterialTheme.shapes.extraLarge)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Order, left to right: dictation, the command palette, the field, the attachment, the send.
        // The paperclip used to sit first — the rarest control in the most reachable place, and the
        // one you press by accident when reaching for the field.
        if (onVoice != null) {
            ComposerControl(
                icon = Icons.Rounded.Mic,
                label = stringResource(R.string.composer_dictate),
                width = LeadingControlWidth,
                onClick = onVoice,
            )
        }
        Box {
            ComposerControl(
                icon = Icons.Rounded.Terminal,
                label = stringResource(R.string.composer_commands),
                width = LeadingControlWidth,
                onClick = { showCommands = true },
            )
            SlashCommandMenu(
                expanded = showCommands,
                onDismiss = { showCommands = false },
                onPick = { command ->
                    showCommands = false
                    onValueChange(SlashCommands.insert(value, command))
                    focus.moveFocus(FocusDirection.Down)
                },
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            maxLines = 6,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(field)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textSecondary,
                        )
                    }
                    inner()
                }
            },
        )
        if (onAttach != null) {
            ComposerControl(
                icon = Icons.Rounded.AttachFile,
                label = stringResource(R.string.composer_attach),
                enabled = !busy,
                badge = attachmentCount.takeIf { it > 0 },
                onClick = onAttach,
            )
        }
        // A photo with no caption is a message: the server names the turn for it.
        SendButton(enabled = (value.isNotBlank() || attachmentCount > 0) && !busy, onClick = onSend)
    }
}

/**
 * A glyph in the capsule: one size, one touch target, one tint.
 *
 * [IconButton] alone is 48 dp while the send disc is 40, so the row used to sit at three different
 * heights depending on which control you looked at.
 */
@Composable
private fun ComposerControl(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    badge: Int? = null,
    /**
     * Narrower than [ComposerControlSize] for the two controls that open the row: at 40 dp each,
     * the 20 dp glyph leaves 20 dp of air between dictation and the palette, which read as a gap
     * in the capsule rather than as two neighbours.
     */
    width: Dp = ComposerControlSize,
    onClick: () -> Unit,
) {
    val colors = LocalMercuryColors.current
    Box(modifier = Modifier.size(width, ComposerControlSize), contentAlignment = Alignment.Center) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(width, ComposerControlSize)) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) colors.textSecondary else colors.textSecondary.copy(alpha = 0.4f),
                modifier = Modifier.size(ComposerControlIcon),
            )
        }
        if (badge != null) {
            // How many files are already waiting: the paperclip alone never said.
            Text(
                text = badge.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.canvas,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 1.dp, end = 1.dp)
                    .clip(CircleShape)
                    .background(colors.accent)
                    .padding(horizontal = 4.dp),
            )
        }
    }
}

/**
 * The daemon's slash commands, as a filtered list hanging off the button that opened it.
 *
 * A menu rather than a bottom sheet: the eye stays on the composer and the conversation above it is
 * not covered. Tapping writes the command into the field — most of these take an argument, and one
 * that sent itself would run without it.
 */
@Composable
private fun SlashCommandMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val colors = LocalMercuryColors.current
    var query by remember { mutableStateOf("") }
    val matched = SlashCommands.matching(query)

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            onDismiss()
            query = ""
        },
        modifier = Modifier.widthIn(min = 280.dp, max = 340.dp),
    ) {
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.composer_commands_search),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                    inner()
                }
            },
        )
        Column(
            modifier = Modifier
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (matched.isEmpty()) {
                Text(
                    text = stringResource(R.string.composer_commands_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            matched.forEachIndexed { index, command ->
                // A heading each time the category changes: readable without a tab bar, and the
                // order is the catalogue's, so a command keeps its place between two openings.
                val previous = matched.getOrNull(index - 1)
                if (previous == null || previous.category != command.category) {
                    Text(
                        text = stringResource(command.category),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(command.command) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = command.command,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.accent,
                    )
                    Text(
                        text = stringResource(command.description),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalMercuryColors.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (enabled) colors.textPrimary else colors.surfaceMuted),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Rounded.ArrowUpward,
                contentDescription = stringResource(R.string.composer_send),
                tint = if (enabled) colors.canvas else colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
