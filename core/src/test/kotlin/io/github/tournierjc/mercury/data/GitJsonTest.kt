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
    fun `porcelain codes become keys, not words`() {
        assertEquals(NoticeKey.GIT_MODIFIED, GitJson.statusNotice("modified").key)
        assertEquals(NoticeKey.GIT_ADDED, GitJson.statusNotice("added").key)
        assertEquals(NoticeKey.GIT_DELETED, GitJson.statusNotice("deleted").key)
        assertEquals(NoticeKey.GIT_RENAMED, GitJson.statusNotice("renamed").key)
        assertEquals(NoticeKey.GIT_UNTRACKED, GitJson.statusNotice("untracked").key)
        // An unknown code travels as the argument rather than being guessed.
        assertEquals(NoticeKey.GIT_OTHER, GitJson.statusNotice("conflicted").key)
        assertEquals("conflicted", GitJson.statusNotice("conflicted").args.single())
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
        assertEquals(
            listOf(NoticeKey.GIT_AHEAD, NoticeKey.GIT_BEHIND),
            GitJson.syncNotices(2, 1).map { it.key },
        )
        assertEquals(listOf("2", "1"), GitJson.syncNotices(2, 1).flatMap { it.args })
        assertEquals(listOf(NoticeKey.GIT_AHEAD), GitJson.syncNotices(3, 0).map { it.key })
        assertEquals(listOf(NoticeKey.GIT_BEHIND), GitJson.syncNotices(0, 1).map { it.key })
        // Nothing to push and nothing to pull is a truth of its own, not an empty line.
        assertEquals(listOf(NoticeKey.GIT_UP_TO_DATE), GitJson.syncNotices(0, 0).map { it.key })
    }

    @Test
    fun `the summary counts each side, and says when the tree is clean`() {
        val both = GitJson.summaryNotice(status)
        assertEquals(NoticeKey.GIT_SUMMARY_BOTH, both.key)
        assertEquals(listOf("1", "2"), both.args)
        assertEquals(NoticeKey.GIT_CLEAN, GitJson.summaryNotice(GitStatusDto()).key)
        assertEquals(NoticeKey.GIT_UNAVAILABLE, GitJson.summaryNotice(null).key)
    }

    @Test
    fun `a change row knows its side and its change`() {
        val staged = GitFileDto("a.kt", "modified", true)
        assertEquals(NoticeKey.GIT_STAGED, GitJson.sideNotice(staged.staged).key)
        assertEquals(NoticeKey.GIT_MODIFIED, GitJson.statusNotice(staged.status).key)

        val untracked = GitFileDto("b.md", "untracked", false)
        assertEquals(NoticeKey.GIT_UNSTAGED, GitJson.sideNotice(untracked.staged).key)
        assertEquals(NoticeKey.GIT_UNTRACKED, GitJson.statusNotice(untracked.status).key)
    }
}
