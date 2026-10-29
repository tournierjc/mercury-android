package io.github.tournierjc.mercury.bots

/**
 * Ce que l'app sait de sa propre visibilité, pour ne pas notifier un message déjà à l'écran.
 *
 * Deux faits, tenus à jour par l'activité et par la fiche d'un équipier : l'app est-elle au
 * premier plan, et quelle fiche est ouverte. Volontairement pas un flux ni un objet injecté :
 * le guetteur vit dans le processus de l'app, et son seul besoin est de répondre oui ou non au
 * moment où un tour se termine.
 */
object AppVisibility {

    @Volatile
    var foreground: Boolean = false

    @Volatile
    var openBotId: String? = null

    /** L'écran de cet équipier est-il celui qu'on regarde ? */
    fun isReading(botId: String): Boolean = foreground && openBotId == botId
}
