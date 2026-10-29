package io.github.tournierjc.mercury.vm

import io.github.tournierjc.mercury.data.NoticeKey
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Le mot de la bannière, sur Objectifs et projets.
 *
 * Les deux écrans écrivent dans la route mémoire, et ils en réutilisaient les notices : ajouter un
 * projet répondait « Souvenir ajouté ». Le propriétaire de l'app ne les appelle jamais des souvenirs,
 * et une confirmation qui nomme le mauvais objet fait douter de ce qui vient d'être enregistré.
 */
class GoalsNoticesTest {

    @Test
    fun `adding on the objectifs tab speaks about objectifs`() {
        assertEquals(NoticeKey.GOAL_ADDED, noticeForAdd(GoalTab.Goals))
    }

    @Test
    fun `adding on the projets tab speaks about projets`() {
        assertEquals(NoticeKey.PROJECT_ADDED, noticeForAdd(GoalTab.Projects))
    }

    @Test
    fun `deleting an objectif speaks about objectifs`() {
        assertEquals(NoticeKey.GOAL_FORGOTTEN, noticeForForget(wasGoal = true))
    }

    @Test
    fun `deleting a projet from the projets tab speaks about projets`() {
        assertEquals(NoticeKey.PROJECT_FORGOTTEN, noticeForForget(wasGoal = false))
    }

    @Test
    fun `no confirmation on these screens borrows the memory wording`() {
        val memory = setOf(NoticeKey.MEMORY_ADDED, NoticeKey.MEMORY_FORGOTTEN, NoticeKey.MEMORY_UPDATED)
        val spoken = GoalTab.entries.map { noticeForAdd(it) } + listOf(noticeForForget(true), noticeForForget(false))
        assertEquals(emptyList<NoticeKey>(), spoken.filter { it in memory })
    }
}
