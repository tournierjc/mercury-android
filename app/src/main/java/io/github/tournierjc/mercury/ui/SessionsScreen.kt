package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
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
import io.github.tournierjc.mercury.data.ThreadDto
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors

/**
 * Every session the daemon knows, with a filter.
 *
 * The drawer lists five: it is a way in, not an archive, and a growing list of sessions was
 * pushing the navigation off the sheet. This screen is where the rest lives — newest first, with
 * a filter that reads the title, the alias and the short id, because that is what the owner has
 * in front of them when they remember a conversation and not its date.
 *
 * The list is the daemon's: nothing is kept on the phone, so two devices show the same sessions.
 */
@Composable
fun SessionsScreen(
    threads: List<ThreadDto>,
    currentSessionId: String?,
    onBack: () -> Unit,
    onOpenThread: (ThreadDto) -> Unit,
    onDeleteThread: (ThreadDto) -> Unit,
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

        when {
            ordered.isEmpty() -> EmptySessions(stringResource(R.string.sessions_empty))
            // Nothing to show is two different states: no session at all, and a filter that keeps
            // none of them. Saying "aucune session" to a filter would read as a deletion.
            filtered.isEmpty() -> EmptySessions(stringResource(R.string.sessions_no_match))
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { thread ->
                    SessionRow(
                        thread = thread,
                        selected = thread.id == currentSessionId,
                        onOpen = { onOpenThread(thread) },
                        onDelete = { pendingDelete = thread },
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
