package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.ViewKanban
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.data.BotJson
import io.github.tournierjc.mercury.data.ThreadDto
import kotlinx.serialization.json.JsonObject
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors

@Composable
fun ThreadDrawer(
    threads: List<ThreadDto>,
    currentSessionId: String?,
    onNewChat: () -> Unit,
    onOpenThread: (ThreadDto) -> Unit,
    onDeleteThread: (ThreadDto) -> Unit,
    onOpenBridge: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenConsole: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenBoards: () -> Unit,
    onOpenBots: () -> Unit,
    onOpenDeliverables: () -> Unit,
    /** The fleet, listed at the top: talking to a teammate is one tap from anywhere. */
    bots: List<JsonObject> = emptyList(),
    avatars: Map<String, ByteArray> = emptyMap(),
    activeBotId: String? = null,
    onOpenBot: (String) -> Unit = {},
    onSignOut: () -> Unit,
) {
    val colors = LocalMercuryColors.current
    var pendingDelete by remember { mutableStateOf<ThreadDto?>(null) }
    ModalDrawerSheet(
        drawerContainerColor = colors.surface,
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        modifier = Modifier.fillMaxHeight(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
            if (bots.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.drawer_section_crew),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                bots.forEach { bot ->
                    val id = BotJson.id(bot)
                    val selected = id == activeBotId
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.extraSmall)
                            .clickable { onOpenBot(id) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        BotFace(bot, avatars[id], small = true)
                        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(
                                text = BotJson.name(bot),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selected) colors.accent else colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = noticeText(BotJson.stateNotice(BotJson.state(bot))),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                        if (BotJson.needsYou(bot)) {
                            Text("⚠", color = colors.danger, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                // The list above is the access: a second entry called « Équipiers » below opened
                // a different screen under the same word. This link keeps the roster reachable
                // without naming the same thing twice.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable { onOpenBots() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Icon(
                        Icons.Rounded.SmartToy,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.drawer_all_crew),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                }
                HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(vertical = 12.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.drawer_section_sessions),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onNewChat) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.drawer_new_session), tint = colors.textPrimary)
                }
            }
            HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(vertical = 10.dp))
            if (threads.isEmpty()) {
                Text(
                    text = stringResource(R.string.drawer_no_session),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(threads, key = { it.id }) { thread ->
                    val selected = thread.id == currentSessionId
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .clickable { onOpenThread(thread) },
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
                        ) {
                            Text(
                                text = thread.title.ifBlank { thread.alias.ifBlank { thread.shortId } },
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selected) colors.accent else colors.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = thread.updatedAt.take(16).replace('T', ' '),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                        }
                        IconButton(onClick = { pendingDelete = thread }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.drawer_delete_session),
                                tint = colors.textSecondary,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.padding(top = 8.dp))
            HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(vertical = 10.dp))
            DrawerEntry(label = stringResource(R.string.drawer_deliverables), icon = Icons.Rounded.FolderOpen, onClick = onOpenDeliverables)
            DrawerEntry(label = stringResource(R.string.drawer_boards), icon = Icons.Rounded.ViewKanban, onClick = onOpenBoards)
            DrawerEntry(label = stringResource(R.string.drawer_memory), icon = Icons.Rounded.Psychology, onClick = onOpenMemory)
            DrawerEntry(label = stringResource(R.string.drawer_console), icon = Icons.Rounded.Terminal, onClick = onOpenConsole)
            DrawerEntry(label = stringResource(R.string.drawer_bridge), icon = Icons.Rounded.PhoneAndroid, onClick = onOpenBridge)
            // Settings last, where a settings entry is looked for: it used to sit between Console and
            // the bridge, i.e. in the middle of the work screens.
            DrawerEntry(label = stringResource(R.string.drawer_settings), icon = Icons.Rounded.Settings, onClick = onOpenSettings)
            HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(vertical = 6.dp))
            pendingDelete?.let { thread ->
                AlertDialog(
                    onDismissRequest = { pendingDelete = null },
                    containerColor = colors.surface,
                    title = { Text(stringResource(R.string.drawer_delete_session_title), color = colors.textPrimary) },
                    text = {
                        Text(
                            text = thread.title.ifBlank { thread.shortId },
                            color = colors.textSecondary,
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val target = thread
                            pendingDelete = null
                            onDeleteThread(target)
                        }) { Text(stringResource(R.string.drawer_delete), color = colors.danger) }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.drawer_cancel)) }
                    },
                )
            }
            // Sign-out used to be a hand-drawn row whose label was dead: only the trailing icon
            // carried the click. Same shape as the entries above it, so the whole row is the target.
            DrawerEntry(
                label = stringResource(R.string.drawer_sign_out),
                icon = Icons.AutoMirrored.Rounded.Logout,
                onClick = onSignOut,
                muted = true,
            )
        }
    }
}

@Composable
private fun DrawerEntry(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    /** A quiet entry (sign-out): same shape and tap target, text and icon toned down. */
    muted: Boolean = false,
) {
    val colors = LocalMercuryColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (muted) colors.textSecondary else colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Icon(icon, contentDescription = null, tint = colors.textSecondary)
    }
}
