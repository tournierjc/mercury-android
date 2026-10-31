package io.github.tournierjc.mercury.data.dto

import kotlinx.serialization.Serializable

/**
 * `GET /api/sandbox/tools` — what a bot can run, and whether it is signed in.
 *
 * The sandbox confines paths, not processes (`run_command` spawns inside the daemon), so a tool
 * installed from the phone is shared by every bot and by the main agent. [description] says what the
 * tool is for; the screen carries the sharing truth once, not per row.
 */
@Serializable
data class SandboxToolDto(
    val id: String = "",
    val label: String = "",
    val description: String = "",
    val present: Boolean = false,
    val path: String = "",
    val version: String = "",
    /** `no`, `unknown` (nothing to sign in, or the check could not answer), or `connected`. */
    val auth: String = "unknown",
    val authDetail: String = "",
    val installable: Boolean = false,
    /** Why it cannot be installed here — an apt tool on a uid-1000 container, for instance. */
    val installBlocked: String = "",
)

@Serializable
data class SandboxToolsPage(val tools: List<SandboxToolDto> = emptyList())

/** `POST /api/sandbox/tools/:id/install` and `GET …/install` — the job, not the result. */
@Serializable
data class InstallRecordDto(
    val id: String = "",
    val startedAt: Long = 0,
    val finishedAt: Long = 0,
    val state: String = "",
    val log: String = "",
)

/** `POST /api/sandbox/tools/:id/connect` — the device flow, with the code to type on the phone. */
@Serializable
data class ConnectFlowDto(
    val id: String = "",
    val state: String = "pending",
    val code: String = "",
    val url: String = "",
    val startedAt: Long = 0,
    val detail: String = "",
)
