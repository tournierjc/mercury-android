package io.github.tournierjc.mercury.data

import io.github.tournierjc.mercury.data.dto.InstanceConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What the owner typed in Réglages › Instance, kept as text because that is what a form holds.
 *
 * An empty field means "I did not touch this", never "write an empty value": `PUT /api/config`
 * would happily store a nameless instance, and the owner's way out of a half-filled field must not
 * be a rename to nothing.
 */
data class InstanceDraft(
    val name: String = "",
    val owner: String = "",
    val defaultProvider: String = "",
    val tokenBudget: String = "",
)

/**
 * The two bodies the instance settings produce, built from the difference with the daemon's copy.
 *
 * Both routes write the whole value they receive, so a field echoed back unchanged is noise in the
 * daemon's config file and in its logs — and it makes "who changed what" unreadable. Building the
 * patch here, as a pure function over the current values, is what lets a JUnit test say so without
 * a screen or a server.
 */
object InstancePatch {

    /** Body for `PUT /api/config`: only the keys that differ, nested under `identity` as the route expects. */
    fun config(current: InstanceConfig, draft: InstanceDraft): JsonObject {
        val identity = buildJsonObject {
            changed(draft.name, current.identity.name)?.let { put("name", it) }
            changed(draft.owner, current.identity.owner)?.let { put("owner", it) }
        }
        val budget = budget(draft.tokenBudget, current.tokenBudget)
        return buildJsonObject {
            if (identity.isNotEmpty()) put("identity", identity)
            changed(draft.defaultProvider, current.defaultProvider)?.let { put("defaultProvider", it) }
            if (budget != null) put("tokenBudget", budget)
        }
    }

    /**
     * `workspace` for `PUT /api/chat/settings`, or null when the folder did not move — the value
     * the caller passes to the route, already trimmed (a trailing space is a typo, not a folder).
     */
    fun workspace(current: String, typed: String): String? = changed(typed, current)

    /** The typed text when it says something new, null when it is blank or equals the daemon's value. */
    private fun changed(typed: String, current: String): String? =
        typed.trim().takeIf { it.isNotEmpty() && it != current.trim() }

    /**
     * Tokens per day. `0` is a value — the daemon reads it as "no ceiling" — so a blank field is the
     * only thing that means "unchanged"; anything that is not a number never leaves the form.
     */
    private fun budget(typed: String, current: Long): Long? {
        val tokens = typed.trim().toLongOrNull() ?: return null
        return tokens.takeIf { it >= 0 && it != current }
    }
}
