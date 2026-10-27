package io.github.tournierjc.mercury.bots

/**
 * The one line the persistent notification of the watch service carries.
 *
 * It lives here, in `core`, because it is the only part of the background watch that can be
 * tested without a device: the service around it is Android wiring, this is the sentence the
 * owner reads in the shade.
 */
object WatchSummary {

    const val TITLE = "Mercury — veille"

    fun text(bots: Int, waiting: Int): String = when {
        bots <= 0 -> "Aucun équipier — connexion au serveur en cours"
        waiting <= 0 -> "${crew(bots)} · rien ne vous attend"
        else -> "${crew(bots)} · ${waitingText(waiting)}"
    }

    private fun crew(count: Int): String = if (count == 1) "1 équipier" else "$count équipiers"

    private fun waitingText(count: Int): String =
        if (count == 1) "1 vous attend" else "$count vous attendent"
}
