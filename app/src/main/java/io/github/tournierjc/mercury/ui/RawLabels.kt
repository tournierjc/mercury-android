package io.github.tournierjc.mercury.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.tournierjc.mercury.R

/**
 * One server word, said in the owner's language.
 *
 * The API speaks in identifiers — `episode`, `preference`, `queued`, `streamable` — and the screens
 * used to print them `uppercase()`, which is how a French interface ended up shouting English
 * vocabulary at its reader. Every known word has a key in `strings_labels.xml`; an unknown one is
 * still shown, de-slugged and capitalised, so a vocabulary the server grows tomorrow degrades to a
 * readable word instead of a blank.
 */
@Composable
fun rawLabel(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    return when (trimmed.lowercase()) {
        "identity" -> stringResource(R.string.raw_identity)
        "preference" -> stringResource(R.string.raw_preference)
        "goal" -> stringResource(R.string.raw_goal)
        "project" -> stringResource(R.string.raw_project)
        "habit" -> stringResource(R.string.raw_habit)
        "decision" -> stringResource(R.string.raw_decision)
        "constraint" -> stringResource(R.string.raw_constraint)
        "relationship" -> stringResource(R.string.raw_relationship)
        "episode" -> stringResource(R.string.raw_episode)
        "reflection" -> stringResource(R.string.raw_reflection)
        "fact" -> stringResource(R.string.raw_fact)
        "person" -> stringResource(R.string.raw_person)
        "emotion" -> stringResource(R.string.raw_emotion)
        "skill" -> stringResource(R.string.raw_skill)
        "idle" -> stringResource(R.string.raw_idle)
        "running" -> stringResource(R.string.raw_running)
        "queued" -> stringResource(R.string.raw_queued)
        "failed" -> stringResource(R.string.raw_failed)
        "paused" -> stringResource(R.string.raw_paused)
        "waiting" -> stringResource(R.string.raw_waiting)
        "completed" -> stringResource(R.string.raw_completed)
        "cancelled" -> stringResource(R.string.raw_cancelled)
        "done" -> stringResource(R.string.raw_done)
        "blocked" -> stringResource(R.string.raw_blocked)
        "tool" -> stringResource(R.string.raw_tool)
        "renderer" -> stringResource(R.string.raw_renderer)
        "channel" -> stringResource(R.string.raw_channel)
        "provider" -> stringResource(R.string.raw_provider)
        "plugin" -> stringResource(R.string.raw_plugin)
        "builtin" -> stringResource(R.string.raw_builtin)
        "community" -> stringResource(R.string.raw_community)
        "free" -> stringResource(R.string.raw_free)
        "paid" -> stringResource(R.string.raw_paid)
        "stdio" -> stringResource(R.string.raw_stdio)
        "http" -> stringResource(R.string.raw_http)
        "streamable" -> stringResource(R.string.raw_streamable)
        "candidate" -> stringResource(R.string.raw_candidate)
        "confirmed" -> stringResource(R.string.raw_confirmed)
        "refuted" -> stringResource(R.string.raw_refuted)
        "superseded" -> stringResource(R.string.raw_superseded)
        "active" -> stringResource(R.string.raw_active)
        "dormant" -> stringResource(R.string.raw_dormant)
        "expired" -> stringResource(R.string.raw_expired)
        else -> trimmed.replace('_', ' ').replace('-', ' ').replaceFirstChar { it.uppercase() }
    }
}
