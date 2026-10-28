package io.github.tournierjc.mercury.bots

import io.github.tournierjc.mercury.data.Notice
import io.github.tournierjc.mercury.data.NoticeKey

/**
 * The one line the persistent notification of the watch service carries.
 *
 * It lives here, in `core`, because it is the only part of the background watch that can be
 * tested without a device: the service around it is Android wiring, this is the sentence the
 * owner reads in the shade.
 */
object WatchSummary {

    /**
     * The parts of the line, so the service can resolve them with the owner's language:
     * `3 équipiers · 1 vous attend`, or `Aucun équipier — connexion au serveur en cours`.
     */
    fun notices(bots: Int, waiting: Int): List<Notice> = when {
        bots <= 0 -> listOf(Notice(NoticeKey.CREW_NONE))
        waiting <= 0 -> listOf(crew(bots), Notice(NoticeKey.CREW_WAITING_NONE))
        else -> listOf(crew(bots), waitingNotice(waiting))
    }

    private fun crew(count: Int): Notice =
        if (count == 1) Notice(NoticeKey.CREW_ONE)
        else Notice(NoticeKey.CREW_MANY, args = listOf(count.toString()))

    private fun waitingNotice(count: Int): Notice =
        if (count == 1) Notice(NoticeKey.CREW_WAITING_ONE)
        else Notice(NoticeKey.CREW_WAITING_MANY, args = listOf(count.toString()))
}
