package io.github.tournierjc.mercury.data.dto

import kotlinx.serialization.Serializable

/**
 * Daemon administration payloads the console now drives: skills install/delete,
 * provider writes and probes, MCP status, shutdown and the workspace root.
 *
 * Routes read from `src/web/api/system.ts`, `providers.ts`, `mcp.ts`,
 * `lifecycle.ts` and `chat.ts` in the Mercury repo — no shape here is guessed.
 */

/**
 * `POST /api/skills/install` — body `{url}`. The daemon accepts an http(s) URL only
 * (`url must start with http:// or https://`); a bare local path is refused.
 * Answers `{success, name, path}`, or 400 `{success:false, error}`.
 */
@Serializable
data class SkillInstallRequest(val url: String)

@Serializable
data class SkillInstallResult(
    val success: Boolean = false,
    val name: String = "",
    val path: String = "",
    val error: String = "",
)

/**
 * `POST /api/skills/install-from-registry` — body `{id, force}`; `id` must be
 * `<category>/<slug>`. Answers `{success, id, version, status, path, webUrl}`.
 */
@Serializable
data class RegistrySkillInstallRequest(val id: String, val force: Boolean? = null)

@Serializable
data class RegistrySkillInstallResult(
    val success: Boolean = false,
    val id: String = "",
    val version: String = "",
    val status: String = "",
    val path: String = "",
    val webUrl: String = "",
    val error: String = "",
)

/**
 * `POST /api/providers/:id` — every field is optional, and an absent field keeps the
 * stored value (`if (body.apiKey !== undefined) p.apiKey = body.apiKey`). Nothing here
 * is ever read back: `GET /api/providers` only returns a masked key.
 */
@Serializable
data class ProviderUpdateRequest(
    val apiKey: String? = null,
    val baseUrl: String? = null,
    val model: String? = null,
    val enabled: Boolean? = null,
    /** OpenRouter only. Empty lists clear the pin; null leaves it untouched. */
    val routing: ProviderRoutingDto? = null,
)

/** Keys and models are read at boot, so a save always answers `restartRequired: true`. */
@Serializable
data class ProviderSaveResult(
    val success: Boolean = false,
    val restartRequired: Boolean = false,
    val error: String = "",
)

/**
 * `POST /api/providers/:id/test` — probes the provider and reports the first ten models
 * of its catalogue. A provider without a key (or, for the keyless ones, without a base
 * URL) answers 400 with `{success:false, error}`.
 */
@Serializable
data class ProviderTestResult(
    val success: Boolean = false,
    val models: List<String> = emptyList(),
    val recommendedModel: String = "",
    val error: String = "",
)

@Serializable
data class McpToolDto(
    val name: String = "",
    val exposedName: String = "",
    val allowed: Boolean = false,
    val autoApprove: Boolean = false,
    val skipped: String? = null,
)

/**
 * One configured MCP server. `state` is `disabled`, `connecting`, `connected`,
 * `reconnecting` or `failed`; `available` is false when the runtime has no MCP manager.
 */
@Serializable
data class McpServerDto(
    val id: String = "",
    val transport: String = "",
    val enabled: Boolean = false,
    val state: String = "",
    val target: String = "",
    val error: String? = null,
    val toolCount: Int = 0,
    val tools: List<McpToolDto> = emptyList(),
)

@Serializable
data class McpServersResponse(
    val available: Boolean = false,
    val servers: List<McpServerDto> = emptyList(),
)

/**
 * `POST /api/shutdown` — 202 `{ok, pid}`. Loopback plus the local attach token are
 * required, so a client off the host gets 403 and the UI says so instead of pretending.
 */
@Serializable
data class ShutdownResult(
    val ok: Boolean = false,
    val pid: Int = 0,
    val error: String = "",
)

/** `PUT /api/workspace/root` — body `{path}`; the directory must already exist. */
@Serializable
data class WorkspaceRootRequest(val path: String)

@Serializable
data class WorkspaceRootResult(
    val success: Boolean = false,
    val workspace: String = "",
    val error: String = "",
)
