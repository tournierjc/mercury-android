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

    /** `mapStatus()` on the daemon side, translated. Unknown codes stay verbatim. */
    fun statusLabel(status: String): String = when (status.lowercase()) {
        "modified" -> "modifié"
        "added" -> "ajouté"
        "deleted" -> "supprimé"
        "renamed" -> "renommé"
        "copied" -> "copié"
        "unmerged" -> "non fusionné"
        "untracked" -> "non suivi"
        "ignored" -> "ignoré"
        "" -> "modifié"
        else -> status
    }

    /** `indexé · modifié` — the side of the index plus what happened to the file. */
    fun changeLabel(file: GitFileDto): String =
        (if (file.staged) "indexé" else "non indexé") + " · " + statusLabel(file.status)

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
    fun syncLabel(ahead: Int, behind: Int): String {
        val parts = buildList {
            if (ahead > 0) add("$ahead à pousser")
            if (behind > 0) add("$behind à tirer")
        }
        return parts.joinToString(" · ").ifBlank { "à jour" }
    }

    /** One-line summary of the working tree, for the card header. */
    fun summaryLabel(status: GitStatusDto?): String {
        if (status == null) return "indisponible"
        val stagedCount = staged(status).size
        val unstagedCount = unstaged(status).size
        return when {
            stagedCount == 0 && unstagedCount == 0 -> "arbre propre"
            stagedCount == 0 -> "$unstagedCount modifié(s)"
            unstagedCount == 0 -> "$stagedCount indexé(s)"
            else -> "$stagedCount indexé(s) · $unstagedCount modifié(s)"
        }
    }
}
