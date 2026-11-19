package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.bots.BotForum
import io.github.tournierjc.mercury.data.BotJson
import io.github.tournierjc.mercury.data.BotOps
import io.github.tournierjc.mercury.data.ThreadDto
import io.github.tournierjc.mercury.data.dto.BotRunDto
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import kotlinx.serialization.json.JsonObject

/**
 * Every conversation the daemon knows, with a filter.
 *
 * The drawer lists five sessions: it is a way in, not an archive, and a growing list of sessions was
 * pushing the navigation off the sheet. This screen is where the rest lives — newest first, with
 * a filter that reads the title, the alias and the short id, because that is what the owner has
 * in front of them when they remember a conversation and not its date.
 *
 * The list is the daemon's: nothing is kept on the phone, so two devices show the same sessions.
 *
 * Under the Mercury sessions sit the teammates' conversations (the forum): a bot's thread is not a
 * session in the daemon's store — its turns run outside the session repository — so it is drawn from
 * its own source of truth, the journal, with the same newest-first reading. Opening one goes to the
 * conversation screen, which rebuilds the whole thread from the journal and the transcripts.
 */
@Composable
fun SessionsScreen(
    threads: List<ThreadDto>,
    currentSessionId: String?,
    /** The roster, as the live feed holds it — the forum is the fleet's conversations. */
    bots: List<JsonObject>,
    /** Per bot, the newest journal lines (see `BotsViewModel.loadForum`). */
    forum: Map<String, List<BotRunDto>>,
    forumLoading: Boolean,
    /** The avatars the roster cached — the forum draws the same faces the drawer does. */
    avatars: Map<String, ByteArray>,
    onBack: () -> Unit,
    onOpenThread: (ThreadDto) -> Unit,
    onDeleteThread: (ThreadDto) -> Unit,
    /** Opens a teammate's conversation — the whole thread, not a summary. */
    onOpenBot: (String) -> Unit,
) {
    val colors = LocalMercuryColors.current
    var query by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<ThreadDto?>(null) }

    // Newest first, whatever order the route returned: a session list read top-down is a list of
    // "the last thing I said", and the daemon's order is not a promise.
    val ordered = remember(threads) { threads.sortedByDescending { it.updatedAt } }
    val filtered = remember(ordered, query) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) {
            ordered
        } else {
            ordered.filter { thread ->
                thread.title.lowercase().contains(needle) ||
                    thread.alias.lowercase().contains(needle) ||
                    thread.shortId.lowercase().contains(needle)
            }
        }
    }
    // The forum's rows are pure (core): the roster decides who is listed, the journal decides what
    // is new. Rebuilt when either source changes; the filter does not reach the crew — a teammate
    // is a conversation, not a remembered title, and hiding one because its id missed the filter
    // would read as a deletion.
    val forumRows = remember(bots, forum) { BotForum.rows(bots, forum) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .safeDrawingPadding(),
    ) {
        ScreenHeader(
            title = stringResource(R.string.sessions_title),
            subtitle = stringResource(R.string.sessions_count, ordered.size),
            onBack = onBack,
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.sessions_filter)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // One list for the whole screen: Mercury's sessions, then the crew's conversations. A
        // filter that keeps no thread used to blank the screen entirely — the forum stayed its own
        // branch below, so the screen read as empty while the crew was one tap away.
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (ordered.isEmpty()) {
                item(key = "sessions-empty") {
                    EmptySessions(stringResource(R.string.sessions_empty))
                }
            } else if (filtered.isEmpty()) {
                // Nothing to show is two different states: no session at all, and a filter that
                // keeps none of them. Saying "aucune session" to a filter would read as a deletion.
                item(key = "sessions-no-match") {
                    EmptySessions(stringResource(R.string.sessions_no_match))
                }
            } else {
                items(filtered, key = { "thread:${it.id}" }) { thread ->
                    SessionRow(
                        thread = thread,
                        selected = thread.id == currentSessionId,
                        onOpen = { onOpenThread(thread) },
                        onDelete = { pendingDelete = thread },
                    )
                }
            }

            if (bots.isNotEmpty()) {
                item(key = "forum-header") {
                    Column {
                        HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(vertical = 12.dp))
                        Text(
                            text = stringResource(R.string.sessions_bots_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                        )
                        HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(vertical = 10.dp))
                    }
                }
                if (forumLoading && forum.isEmpty()) {
                    item(key = "forum-loading") {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.accent, strokeWidth = 2.dp)
                        }
                    }
                }
                items(forumRows, key = { "bot:${it.id}" }) { row ->
                    BotSessionRow(
                        row = row,
                        face = avatars[row.id],
                        onOpen = { onOpenBot(row.id) },
                    )
                }
            }
        }
    }

    pendingDelete?.let { doomed ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.sessions_delete_title)) },
            text = {
                Text(
                    doomed.title.ifBlank { doomed.alias.ifBlank { doomed.shortId } },
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteThread(doomed)
                    pendingDelete = null
                }) { Text(stringResource(R.string.sessions_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun EmptySessions(text: String) {
    val colors = LocalMercuryColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textSecondary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
    )
}

@Composable
private fun SessionRow(
    thread: ThreadDto,
    selected: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalMercuryColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 20.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = thread.title.ifBlank { thread.alias.ifBlank { thread.shortId } },
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) colors.accent else colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOf(thread.shortId, formatWhen(thread.updatedAt)).filterNotNull().joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = stringResource(R.string.drawer_delete_session),
                tint = colors.textSecondary,
            )
        }
    }
}

/**
 * One teammate's conversation in the forum: the bot's face, its last turn, and when.
 *
 * The subtitle is the journal's own reading — the same words the run list uses (`runStateNotice`,
 * `dateLabel`), so a row here and a row on the bot's sheet cannot disagree about what "réussi"
 * means. A bot that has never run says so with its roster state instead of inventing a date.
 */
@Composable
private fun BotSessionRow(
    row: BotForum.Row,
    face: ByteArray?,
    onOpen: () -> Unit,
) {
    val colors = LocalMercuryColors.current
    val newest = row.newest
    val name = BotJson.name(row.bot)
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
    ) {
        BotFace(bot = row.bot, avatar = face, small = true)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            val preview = when {
                newest == null -> ""
                newest.summary.isNotBlank() -> newest.summary.take(120)
                newest.error.isNotBlank() -> newest.error.take(120)
                else -> ""
            }
            val whenLine = if (newest == null) {
                noticeText(context, BotJson.stateNotice(BotJson.state(row.bot)))
            } else {
                listOf(
                    BotOps.runStateNotice(newest.state)?.let { noticeText(context, it) },
                    BotOps.dateLabel(if (newest.turnStartedAt > 0) newest.turnStartedAt else newest.startedAt),
                )
                    .filterNotNull()
                    .filter { it.isNotBlank() && it != "—" }
                    .joinToString(" · ")
            }
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = preview,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = whenLine,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
        IconButton(onClick = onOpen) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = stringResource(R.string.sessions_open_bot, BotJson.name(row.bot)),
                tint = colors.accent,
            )
        }
    }
}
