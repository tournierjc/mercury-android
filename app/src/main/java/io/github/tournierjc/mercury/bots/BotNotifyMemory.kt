package io.github.tournierjc.mercury.bots

import android.content.Context

/**
 * Les bots auxquels le propriétaire a parlé, retenus d'une exécution à l'autre.
 *
 * C'est ce qui fait d'une conversation un abonnement : sans ça, l'app oubliait à chaque
 * démarrage qu'on lui avait écrit, et un équipier qui n'est pas leader redevenait muet — ou
 * l'inverse, bavard pour tout le monde, selon la règle par défaut.
 *
 * Une écriture par envoi, une lecture par rangement de notification : les préférences d'un
 * processus, sur un ensemble de la taille de l'équipage.
 */
class BotNotifyMemory(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("mercury_bot_spoken", Context.MODE_PRIVATE)

    /** Le propriétaire vient de lui écrire : ce bot pourra sonner. */
    fun mark(botId: String) {
        if (botId.isBlank()) return
        prefs.edit().putBoolean(key(botId), true).apply()
    }

    fun spokenTo(botId: String): Boolean = botId.isNotBlank() && prefs.getBoolean(key(botId), false)

    /** Oublier une conversation : le bot redevient muet (et se tait jusqu'à la prochaine). */
    fun forget(botId: String) {
        prefs.edit().remove(key(botId)).apply()
    }

    private fun key(botId: String) = "spoken:$botId"
}
