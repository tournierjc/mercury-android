package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.ViewKanban
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
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.data.BotJson
import io.github.tournierjc.mercury.data.ThreadDto
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import kotlinx.serialization.json.JsonObject

@Composable
fun ThreadDrawer(
    threads: List<ThreadDto>,
    currentSessionId: String?,
    onNewChat: () -> Unit,
    onOpenThread: (ThreadDto) -> Unit,
    onDeleteThread: (ThreadDto) -> Unit,
    onOpenBot: (String) -> Unit = {},
    onSignOut: () -> Unit,
    /** The screen on top, so the drawer marks where the owner is. */
    current: AppScreen = AppScreen.Chat,
    /** The drawer's whole navigation goes through here — one callback, nineteen screens. */
    onOpen: (AppScreen) -> Unit = {},
    /** The fleet, listed at the top: talking to a teammate is one tap from anywhere. */
    bots: List<JsonObject> = emptyList(),
    avatars: Map<String, ByteArray> = emptyMap(),
    activeBotId: String? = null,
) {
    val colors = LocalMercuryColors.current
    var pendingDelete by remember { mutableStateOf<ThreadDto?>(null) }

    val rows = remember(bots, threads, current) {
        drawerRows(
            botIds = bots.map { BotJson.id(it) },
            threadIds = threads.map { it.id },
            current = current,
        )
    }
    val botsById = remember(bots) { bots.associateBy { BotJson.id(it) } }
    val threadsById = remember(threads) { threads.associateBy { it.id } }

    ModalDrawerSheet(
        drawerContainerColor = colors.surface,
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        modifier = Modifier.fillMaxHeight(),
    ) {
        // One list for the whole sheet: a crew of two and a crew of forty scroll the same way, and
        // the sessions can no longer be squeezed out by a growing crew (they were the only weighted
        // child, so they paid for every row added above them).
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        ) {
            items(
                items = rows,
                key = { row ->
                    when (row) {
                        is DrawerRow.Bot -> "bot:${row.id}"
                        is DrawerRow.Thread -> "thread:${row.id}"
                        else -> row.toString()
                    }
                },
            ) { row ->
                when (row) {
                    is DrawerRow.CrewHeader -> Text(
                        text = stringResource(R.string.drawer_section_crew),
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )

                    is DrawerRow.Bot -> {
                        val bot = botsById[row.id] ?: return@items
                        val selected = row.id == activeBotId
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.extraSmall)
                                .clickable { onOpenBot(row.id) }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                        ) {
                            BotFace(bot, avatars[row.id], small = true)
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
                    is DrawerRow.AllCrew -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.extraSmall)
                            .clickable { onOpen(AppScreen.Bots) }
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

                    is DrawerRow.SessionHeader -> {
                        HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(vertical = 12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.drawer_section_sessions),
                                style = MaterialTheme.typography.headlineSmall,
                                color = colors.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = onNewChat) {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = stringResource(R.string.drawer_new_session),
                                    tint = colors.textPrimary,
                                )
                            }
                        }
                        HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(vertical = 10.dp))
                    }

                    is DrawerRow.NoSession -> Text(
                        text = stringResource(R.string.drawer_no_session),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )

                    is DrawerRow.Thread -> {
                        val thread = threadsById[row.id] ?: return@items
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

                    // The webapp's three groups, named: a flat list of nineteen entries would be
                    // a phone book, and the groups are what makes the order learnable.
                    is DrawerRow.SectionHeader -> Text(
                        text = stringResource(
                            when (row.section) {
                                DrawerSection.SecondBrain -> R.string.nav_section_second_brain
                                DrawerSection.Configure -> R.string.nav_section_configure
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp, start = 12.dp),
                    )

                    is DrawerRow.Screen -> DrawerEntry(
                        label = stringResource(screenLabel(row.screen)),
                        icon = screenIcon(row.screen),
                        onClick = { onOpen(row.screen) },
                        accent = row.current,
                    )

                    is DrawerRow.NavDivider ->
                        HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(vertical = 10.dp))

                    is DrawerRow.SignOut -> DrawerEntry(
                        label = stringResource(R.string.drawer_sign_out),
                        icon = Icons.AutoMirrored.Rounded.Logout,
                        onClick = onSignOut,
                        muted = true,
                    )
                }
            }
        }
    }

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
}

@Composable
private fun DrawerEntry(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    /** The screen on top: same row, accent tint, so the drawer says where the owner is. */
    accent: Boolean = false,
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
            color = when {
                accent -> colors.accent
                muted -> colors.textSecondary
                else -> colors.textPrimary
            },
            modifier = Modifier.weight(1f),
        )
        Icon(
            icon,
            contentDescription = null,
            tint = if (accent) colors.accent else colors.textSecondary,
        )
    }
}

/** The label of every destination, one place, so the drawer and the screens cannot disagree. */
private fun screenLabel(screen: AppScreen): Int = when (screen) {
    AppScreen.Status -> R.string.nav_status
    AppScreen.Chat -> R.string.nav_chat
    AppScreen.Bots -> R.string.nav_bots
    AppScreen.Tasks -> R.string.nav_tasks
    AppScreen.Boards -> R.string.nav_boards
    AppScreen.Workspace -> R.string.nav_workspace
    AppScreen.Memory -> R.string.nav_memory
    AppScreen.Persons -> R.string.nav_persons
    AppScreen.Goals -> R.string.nav_goals
    AppScreen.Graph -> R.string.nav_graph
    AppScreen.Providers -> R.string.nav_providers
    AppScreen.Skills -> R.string.nav_skills
    AppScreen.Mcp -> R.string.nav_mcp
    AppScreen.Plugins -> R.string.nav_plugins
    AppScreen.Permissions -> R.string.nav_permissions
    AppScreen.Schedules -> R.string.nav_schedules
    AppScreen.Usage -> R.string.nav_usage
    AppScreen.Deliverables -> R.string.nav_deliverables
    AppScreen.Bridge -> R.string.nav_bridge
    AppScreen.Console -> R.string.nav_console
    AppScreen.Settings -> R.string.nav_settings
}

private fun screenIcon(screen: AppScreen): ImageVector = when (screen) {
    AppScreen.Status -> Icons.Rounded.SpaceDashboard
    AppScreen.Chat -> Icons.AutoMirrored.Rounded.Chat
    AppScreen.Bots -> Icons.Rounded.SmartToy
    AppScreen.Tasks -> Icons.Rounded.Checklist
    AppScreen.Boards -> Icons.Rounded.ViewKanban
    AppScreen.Workspace -> Icons.Rounded.Code
    AppScreen.Memory -> Icons.Rounded.Psychology
    AppScreen.Persons -> Icons.Rounded.Group
    AppScreen.Goals -> Icons.Rounded.Flag
    AppScreen.Graph -> Icons.Rounded.Share
    AppScreen.Providers -> Icons.Rounded.Memory
    AppScreen.Skills -> Icons.Rounded.Extension
    AppScreen.Mcp -> Icons.Rounded.Cable
    AppScreen.Plugins -> Icons.Rounded.Widgets
    AppScreen.Permissions -> Icons.Rounded.Shield
    AppScreen.Schedules -> Icons.Rounded.Schedule
    AppScreen.Usage -> Icons.Rounded.BarChart
    AppScreen.Deliverables -> Icons.Rounded.FolderOpen
    AppScreen.Bridge -> Icons.Rounded.PhoneAndroid
    AppScreen.Console -> Icons.Rounded.Terminal
    AppScreen.Settings -> Icons.Rounded.Settings
}
