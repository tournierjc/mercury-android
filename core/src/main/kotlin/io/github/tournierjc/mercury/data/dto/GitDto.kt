package io.github.tournierjc.mercury.data.dto

import kotlinx.serialization.Serializable

/**
 * `workspace-ide.ts` — the eleven `/api/git/…` routes.
 *
 * Every route answers a plain object; on failure the daemon replies
 * `{error: "<git stderr>"}` with a 4xx/5xx, so action routes decode [SimpleResponse]
 * (`{success}` / `{message}`) and the read routes carry their own envelope.
 */

/** One changed path. `status` is `modified`, `added`, `deleted`, `renamed`, `untracked`… */
@Serializable
data class GitFileDto(
    val path: String = "",
    val status: String = "",
    val staged: Boolean = false,
)

/**
 * `GET /api/git/status` — branch, upstream distance and the changed files.
 *
 * A path that is both staged and edited again appears twice (once per side), which is
 * exactly what the index and the working tree say.
 */
@Serializable
data class GitStatusDto(
    val branch: String = "",
    val upstream: String = "",
    val ahead: Int = 0,
    val behind: Int = 0,
    val files: List<GitFileDto> = emptyList(),
    val cwd: String = "",
    val error: String = "",
)

@Serializable
data class GitBranchDto(
    val name: String = "",
    val isCurrent: Boolean = false,
    val upstream: String = "",
)

@Serializable
data class GitBranchesDto(
    val branches: List<GitBranchDto> = emptyList(),
    val error: String = "",
)

/** `%H||%h||%an||%ae||%ar||%s` — `date` is git's relative form ("2 hours ago"). */
@Serializable
data class GitCommitDto(
    val hash: String = "",
    val short: String = "",
    val author: String = "",
    val email: String = "",
    val date: String = "",
    val message: String = "",
)

@Serializable
data class GitLogDto(
    val commits: List<GitCommitDto> = emptyList(),
    val error: String = "",
)

/** `GET /api/git/diff` — the raw patch, kept as text: there is no renderer here. */
@Serializable
data class GitDiffDto(
    val diff: String = "",
    val error: String = "",
)

/** `POST /api/git/generate-commit-message` — the model's message, or `{error}`. */
@Serializable
data class CommitMessageDto(
    val message: String = "",
    val error: String = "",
)
