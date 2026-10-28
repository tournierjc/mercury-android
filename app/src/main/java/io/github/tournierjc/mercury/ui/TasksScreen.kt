package io.github.tournierjc.mercury.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import io.github.tournierjc.mercury.MercuryApp
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.ui.theme.LocalMercuryColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Tasks — the webapp's own /tasks page is a "coming soon" card, so parity is honest: this screen
 * says the same thing, and shows the one task truth the API does expose, the agents currently
 * running. It is not a fake list: when the server grows a task queue, this is where it lands.
 */
@Composable
fun TasksScreen(onBack: () -> Unit) {
    val colors = LocalMercuryColors.current
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MercuryApp).container }
    var agents by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var available by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching { container.api.getJson("/api/agents") }
                .onSuccess { root ->
                    available = (root["available"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull()
                    agents = (root["agents"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
                }
                .onFailure { available = false }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = stringResource(R.string.nav_tasks), onBack = onBack)

        CollapsibleSection(stringResource(R.string.tasks_agents_titre), defaultOpen = true) {
            if (agents.isEmpty()) {
                Text(
                    text = if (available == false) {
                        stringResource(R.string.tasks_agents_indisponible)
                    } else {
                        stringResource(R.string.tasks_aucun_agent)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            agents.forEach { agent ->
                ActionRow(
                    title = agent["id"]?.jsonPrimitive?.content ?: stringResource(R.string.tasks_agent_sans_id),
                    subtitle = listOfNotNull(
                        agent["state"]?.jsonPrimitive?.content,
                        agent["startedAt"]?.jsonPrimitive?.content,
                    ).joinToString(" · "),
                    trailing = "",
                    actions = emptyList(),
                    // No handler: the task page of the webapp has nothing to open yet, and a row
                    // that answers the finger while doing nothing is a lie.
                    onClick = null,
                )
            }
        }

        CollapsibleSection(stringResource(R.string.tasks_file_titre), defaultOpen = true) {
            Text(
                text = stringResource(R.string.tasks_file_corps),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}
