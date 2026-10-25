package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tournierjc.mercury.data.ThreadDto
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors

@Composable
fun ThreadDrawer(
    threads: List<ThreadDto>,
    currentSessionId: String?,
    onNewChat: () -> Unit,
    onOpenThread: (ThreadDto) -> Unit,
    onOpenBridge: () -> Unit,
    onSignOut: () -> Unit,
) {
    val colors = LocalMercuryColors.current
    ModalDrawerSheet(
        drawerContainerColor = colors.surface,
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        modifier = Modifier.fillMaxHeight(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Sessions",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onNewChat) {
                    Icon(Icons.Rounded.Add, contentDescription = "Nouvelle session", tint = colors.textPrimary)
                }
            }
            HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(vertical = 10.dp))
            if (threads.isEmpty()) {
                Text(
                    text = "Aucune session pour l'instant.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(threads, key = { it.id }) { thread ->
                    val selected = thread.id == currentSessionId
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpenThread(thread) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
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
                }
            }
            Spacer(modifier = Modifier.padding(top = 8.dp))
            HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(vertical = 10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onOpenBridge() }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "Pont appareil",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Rounded.PhoneAndroid,
                    contentDescription = null,
                    tint = colors.textSecondary,
                )
            }
            HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(vertical = 6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Se déconnecter",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onSignOut) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Se déconnecter", tint = colors.textSecondary)
                }
            }
        }
    }
}
