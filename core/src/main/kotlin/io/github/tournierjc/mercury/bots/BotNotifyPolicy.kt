package io.github.tournierjc.mercury.bots

/**
 * Qui a le droit de faire sonner le téléphone.
 *
 * Un job de flotte produit un tour par équipier : prévenir de chacun, c'est recevoir six fois la
 * même nouvelle, et le propriétaire finit par couper les notifications — c'est-à-dire par perdre
 * aussi celles qui comptaient. Le **leader** est donc la voix d'une flotte : c'est lui qui rend
 * compte du travail de son équipe, dans le fil du job.
 *
 * Un équipier qui n'est pas leader sonne pour une seule raison : le propriétaire lui a parlé. Une
 * conversation est un abonnement — elle survit à la fermeture de l'app (elle est mémorisée côté
 * téléphone), donc la réponse attendue arrive bien sur le téléphone même si l'app est tuée.
 *
 * Ce que le propriétaire a sous les yeux ne sonne pas : lire un fil est déjà la notification.
 */
object BotNotifyPolicy {

    /** La règle de fond : le leader, ou un bot auquel on a parlé. */
    fun rings(isLead: Boolean, spokenTo: Boolean): Boolean = isLead || spokenTo

    /**
     * @param isLead      ce bot mène une flotte (`fleetRole == "lead"`)
     * @param spokenTo    le propriétaire lui a déjà écrit depuis ce téléphone
     * @param foreground  l'app est au premier plan
     * @param openBotId   le fil ouvert à l'écran, s'il y en a un
     */
    fun shouldPost(
        isLead: Boolean,
        spokenTo: Boolean,
        foreground: Boolean,
        openBotId: String?,
        botId: String,
    ): Boolean {
        if (foreground && openBotId == botId) return false
        return rings(isLead, spokenTo)
    }
}
