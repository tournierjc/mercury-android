package io.github.tournierjc.mercury.data.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Second-brain payloads (memory, beliefs, persons, graph) plus the read-only
 * runtime config. Captured from a live 1.4.2 instance — every field has a
 * default so a Mercury patch adding keys cannot break a screen.
 */

/** The ten memory types Mercury accepts on `POST /api/brain/memory`. */
val MEMORY_TYPES: List<String> = listOf(
    "identity",
    "preference",
    "goal",
    "project",
    "habit",
    "decision",
    "constraint",
    "relationship",
    "episode",
    "reflection",
)

@Serializable
data class MemoryDto(
    val id: String = "",
    val type: String = "",
    val summary: String = "",
    val detail: String? = null,
    val scope: String = "",
    val evidenceKind: String = "",
    val source: String = "",
    val confidence: Double = 0.0,
    val importance: Double = 0.0,
    val durability: Double = 0.0,
    val evidenceCount: Int = 0,
    val dismissed: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val lastSeenAt: Long = 0,
)

@Serializable
data class MemoriesPage(
    val memories: List<MemoryDto> = emptyList(),
    val total: Int = 0,
    val available: Boolean = true,
    val error: String = "",
)

@Serializable
data class AddMemoryRequest(
    val type: String,
    val summary: String,
    val detail: String? = null,
    val importance: Double? = null,
)

/** `PUT /api/brain/memory/:id` — absent fields keep their value. */
@Serializable
data class UpdateMemoryRequest(
    val summary: String? = null,
    val detail: String? = null,
    val importance: Double? = null,
    val confidence: Double? = null,
)

@Serializable
data class BeliefDto(
    val id: String = "",
    val ownerKey: String = "",
    val kind: String = "",
    val dimension: String? = null,
    val level: String? = null,
    val statement: String = "",
    val detail: String? = null,
    val context: String = "",
    /** A weighted belief count: the server sends 4.5, not 4 — an Int here threw the whole page away. */
    val support: Double = 0.0,
    val against: Double = 0.0,
    val supportSessions: Double = 0.0,
    val timescale: String = "",
    val status: String = "",
    val ownerStated: Boolean = false,
    val validFrom: Long = 0,
    val validTo: Long? = null,
    val supersededBy: String? = null,
    val lastSupportedAt: Long = 0,
) {
    /** Support minus contradiction, the number the dashboard sorts on. */
    val netSupport: Double get() = support - against
}

@Serializable
data class BeliefsPage(
    val beliefs: List<BeliefDto> = emptyList(),
    val total: Int = 0,
    val available: Boolean = true,
    val error: String = "",
)

/**
 * Persons are only exposed by Mercury once the brain has met someone, and the
 * list is empty on a fresh instance — so the entry shape stays untyped rather
 * than invented.
 */
@Serializable
data class PersonsPage(
    val persons: List<JsonObject> = emptyList(),
    val total: Int = 0,
    val available: Boolean = true,
)

@Serializable
data class GraphNode(
    val id: String = "",
    val label: String = "",
    val fullLabel: String = "",
    val type: String = "",
    val importance: Double = 0.0,
    val confidence: Double = 0.0,
    val color: String = "",
    val size: Double = 0.0,
)

@Serializable
data class BeliefGraphDto(
    val nodes: List<GraphNode> = emptyList(),
    val edges: List<JsonObject> = emptyList(),
    val available: Boolean = true,
)

@Serializable
data class IdentityDto(
    val name: String = "",
    val owner: String = "",
    val creator: String = "",
    val about: String = "",
)

/** `GET /api/config` projects the runtime config down to these keys. */
@Serializable
data class ConfigDto(
    val identity: IdentityDto = IdentityDto(),
    val defaultProvider: String = "",
    val tokenBudget: Long = 0,
    val heartbeatInterval: Long = 0,
    val telegramEnabled: Boolean = false,
    val secondBrainEnabled: Boolean = false,
)

@Serializable
data class WorkspaceFileDto(
    val path: String = "",
    val name: String = "",
    val content: String = "",
    val size: Long = 0,
    val binary: Boolean = false,
    val truncated: Boolean = false,
    val error: String = "",
) {
    val readable: Boolean get() = error.isBlank() && !binary
}
