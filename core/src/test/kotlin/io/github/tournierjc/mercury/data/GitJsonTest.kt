package io.github.tournierjc.mercury.data

import io.github.tournierjc.mercury.data.dto.GitFileDto
import io.github.tournierjc.mercury.data.dto.GitStatusDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading a status for the card: which side of the index each path sits on, and a sync
 * line that says three different things (`à jour`, `2 à pousser`, `sans amont`).
 */
class GitJsonTest {

    private val status = GitStatusDto(
        branch = "feat/git-spotify",
        upstream = "origin/feat/git-spotify",
        ahead = 2,
        behind = 1,
        files = listOf(
            GitFileDto("core/MercuryApi.kt", "modified", staged = true),
            GitFileDto("core/MercuryApi.kt", "modified", staged = false),
            GitFileDto("docs/notes.md", "untracked", staged = false),
        ),
    )

    @Test
    fun `porcelain codes become French labels`() {
        assertEquals("modifié", GitJson.statusLabel("modified"))
        assertEquals("ajouté", GitJson.statusLabel("added"))
        assertEquals("supprimé", GitJson.statusLabel("deleted"))
        assertEquals("renommé", GitJson.statusLabel("renamed"))
        assertEquals("non suivi", GitJson.statusLabel("untracked"))
        // An unknown code is shown as the daemon sent it rather than guessed.
        assertEquals("conflicted", GitJson.statusLabel("conflicted"))
    }

    @Test
    fun `a path appears on both sides without being merged`() {
        assertEquals(listOf("core/MercuryApi.kt"), GitJson.staged(status).map { it.path })
        assertEquals(
            listOf("core/MercuryApi.kt", "docs/notes.md"),
            GitJson.unstaged(status).map { it.path },
        )
    }

    @Test
    fun `staging only offers the unstaged side`() {
        assertEquals(listOf("core/MercuryApi.kt", "docs/notes.md"), GitJson.stageable(status))
        assertEquals(listOf("core/MercuryApi.kt"), GitJson.unstageable(status))
    }

    @Test
    fun `blank paths never reach the stage route`() {
        val odd = GitStatusDto(files = listOf(GitFileDto("", "modified", staged = false)))
        assertTrue(GitJson.stageable(odd).isEmpty())
    }

    @Test
    fun `the sync line separates ahead from behind`() {
        assertEquals("2 à pousser · 1 à tirer", GitJson.syncLabel(2, 1))
        assertEquals("3 à pousser", GitJson.syncLabel(3, 0))
        assertEquals("1 à tirer", GitJson.syncLabel(0, 1))
        assertEquals("à jour", GitJson.syncLabel(0, 0))
    }

    @Test
    fun `the summary counts each side, and says when the tree is clean`() {
        assertEquals("1 indexé(s) · 2 modifié(s)", GitJson.summaryLabel(status))
        assertEquals("arbre propre", GitJson.summaryLabel(GitStatusDto()))
        assertEquals("indisponible", GitJson.summaryLabel(null))
    }

    @Test
    fun `a change row says the side and the change`() {
        assertEquals("indexé · modifié", GitJson.changeLabel(GitFileDto("a.kt", "modified", true)))
        assertEquals("non indexé · non suivi", GitJson.changeLabel(GitFileDto("b.md", "untracked", false)))
    }
}
