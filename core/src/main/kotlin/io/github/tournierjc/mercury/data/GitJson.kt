package io.github.tournierjc.mercury.data

import io.github.tournierjc.mercury.data.dto.GitFileDto
import io.github.tournierjc.mercury.data.dto.GitStatusDto

/**
 * Reading `GET /api/git/status` for the console card: French labels for git's porcelain
 * codes, an honest sync line, and the two sides of the index as separate lists.
 *
 * Git reports a path once per side, so a file staged and then edited again is both
 * `indexé` and `modifié`: the card must be able to stage or unstage each side without
 * inventing a merged state.
 */
object GitJson {

    /**
     * `mapStatus()` on the daemon side, as a key: the words belong to the screen (two languages).
     * A code this app does not know travels as the argument, verbatim, rather than being guessed.
     */
    fun statusNotice(status: String): Notice = when (status.lowercase()) {
        "modified" -> Notice(NoticeKey.GIT_MODIFIED).asInfo()
        "added" -> Notice(NoticeKey.GIT_ADDED).asInfo()
        "deleted" -> Notice(NoticeKey.GIT_DELETED).asInfo()
        "renamed" -> Notice(NoticeKey.GIT_RENAMED).asInfo()
        "copied" -> Notice(NoticeKey.GIT_COPIED).asInfo()
        "unmerged" -> Notice(NoticeKey.GIT_UNMERGED).asInfo()
        "untracked" -> Notice(NoticeKey.GIT_UNTRACKED).asInfo()
        "ignored" -> Notice(NoticeKey.GIT_IGNORED).asInfo()
        "" -> Notice(NoticeKey.GIT_MODIFIED).asInfo()
        else -> Notice(NoticeKey.GIT_OTHER, args = listOf(status)).asInfo()
    }

    /** Which side of the index the row is on: `indexé` or `non indexé`. */
    fun sideNotice(staged: Boolean): Notice =
        Notice(if (staged) NoticeKey.GIT_STAGED else NoticeKey.GIT_UNSTAGED).asInfo()

    fun staged(status: GitStatusDto?): List<GitFileDto> =
        status?.files.orEmpty().filter { it.staged }.distinctBy { it.path }

    fun unstaged(status: GitStatusDto?): List<GitFileDto> =
        status?.files.orEmpty().filterNot { it.staged }.distinctBy { it.path }

    /** Paths to hand to `POST /api/git/stage` — never the ones already staged. */
    fun stageable(status: GitStatusDto?): List<String> =
        unstaged(status).map { it.path }.filter { it.isNotBlank() }

    /** Paths to hand to `POST /api/git/unstage`. */
    fun unstageable(status: GitStatusDto?): List<String> =
        staged(status).map { it.path }.filter { it.isNotBlank() }

    /**
     * `2 à pousser · 1 à tirer`, `à jour` when the branch matches its upstream, and
     * `sans amont` when git tracks nothing — three different truths the card must not blur.
     */
    fun syncNotices(ahead: Int, behind: Int): List<Notice> {
        val parts = buildList {
            if (ahead > 0) add(Notice(NoticeKey.GIT_AHEAD, args = listOf(ahead.toString())).asInfo())
            if (behind > 0) add(Notice(NoticeKey.GIT_BEHIND, args = listOf(behind.toString())).asInfo())
        }
        return parts.ifEmpty { listOf(Notice(NoticeKey.GIT_UP_TO_DATE).asInfo()) }
    }

    /** One-line summary of the working tree, for the card header. */
    /** `arbre propre`, `3 modifié(s)`… as keys: the counts are arguments, the words resources. */
    fun summaryNotice(status: GitStatusDto?): Notice {
        if (status == null) return Notice(NoticeKey.GIT_UNAVAILABLE).asInfo()
        val stagedCount = staged(status).size
        val unstagedCount = unstaged(status).size
        return when {
            stagedCount == 0 && unstagedCount == 0 -> Notice(NoticeKey.GIT_CLEAN).asInfo()
            stagedCount == 0 -> Notice(NoticeKey.GIT_SUMMARY_UNSTAGED, args = listOf(unstagedCount.toString())).asInfo()
            unstagedCount == 0 -> Notice(NoticeKey.GIT_SUMMARY_STAGED, args = listOf(stagedCount.toString())).asInfo()
            else -> Notice(
                NoticeKey.GIT_SUMMARY_BOTH,
                args = listOf(stagedCount.toString(), unstagedCount.toString()),
            ).asInfo()
        }
    }
}
