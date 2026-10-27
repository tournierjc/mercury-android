package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors

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
    hint: String = "Demande à Mercury…",
    modifier: Modifier = Modifier,
) {
    val colors = LocalMercuryColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surface)
            // Same hairline as a rounded field: without it the composer melts into the canvas.
            .border(1.dp, colors.hairlineStrong, RoundedCornerShape(28.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onAttach != null) {
            IconButton(onClick = onAttach, enabled = !busy) {
                Icon(
                    imageVector = Icons.Rounded.AttachFile,
                    contentDescription = "Joindre une photo ou un PDF",
                    tint = colors.textSecondary,
                )
            }
        }
        if (onVoice != null) {
            IconButton(onClick = onVoice) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = "Dicter",
                    tint = colors.textSecondary,
                )
            }
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
                .padding(horizontal = 4.dp, vertical = 10.dp),
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
        // A photo with no caption is a message: the server names the turn for it.
        SendButton(enabled = (value.isNotBlank() || attachmentCount > 0) && !busy, onClick = onSend)
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
                contentDescription = "Envoyer",
                tint = if (enabled) colors.canvas else colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
