package io.github.tournierjc.mercury.bots

/**
 * Qui a le droit de faire sonner le téléphone.
 *
 * La règle d'avant était une propriété du bot — « est-il leader ? lui ai-je déjà parlé ? » — et
 * c'est ce qui n'allait pas : « lui ai-je déjà parlé » ne se périme jamais, donc un équipier à qui
 * l'owner avait écrit une fois sonnait pour toutes ses tâches, y compris celles qu'un job de flotte
 * venait de lui confier. Six équipiers, six sonneries pour une seule nouvelle.
 *
 * La question n'est pas qui est ce bot, mais **qu'est-ce qui a produit ce tour** :
 *
 *  - `chat` : l'owner vient de lui écrire dans l'app — la réponse est attendue, elle sonne ;
 *  - `cron` : une routine planifiée a rendu son résultat — c'est un livrable, il sonne ;
 *  - sinon (`mailbox`, `api`, `web`…), c'est un tour de travail : il ne sonne que si le bot est le
 *    **leader**, parce que c'est lui qui rend compte d'une mission demandée à sa flotte.
 *
 * Un daemon antérieur à ce champ n'envoie pas d'origine : on retombe alors sur l'ancienne règle,
 * plutôt que de faire taire des nouvelles qu'on ne sait pas classer.
 *
 * Ce que l'owner a sous les yeux ne sonne pas : lire un fil est déjà la notification.
 */
object BotNotifyPolicy {

    /**
     * La règle de fond.
     *
     * @param trigger l'origine du tour (`chat`, `cron`, `mailbox`…), vide si le daemon ne la dit pas
     * @param isLead  ce bot mène une flotte (`fleetRole == "lead"`)
     * @param spokenTo l'owner lui a déjà écrit depuis ce téléphone — secours seul, quand l'origine
     *   manque
     */
    fun rings(trigger: String, isLead: Boolean, spokenTo: Boolean): Boolean = when (trigger) {
        // Une conversation : la réponse à ce qu'il vient d'écrire.
        "chat" -> true
        // Une routine planifiée : son résultat est un livrable, même d'un simple équipier.
        "cron" -> true
        // Un tour de travail venu d'une mission : le leader en est la voix.
        "mailbox", "api", "web", "cloud", "telegram" -> isLead
        // Origine inconnue (daemon plus ancien) : ne rien faire taire par ignorance.
        else -> isLead || spokenTo
    }

    /**
     * @param trigger     l'origine du tour
     * @param isLead      ce bot mène une flotte
     * @param spokenTo    l'owner lui a déjà écrit depuis ce téléphone
     * @param foreground  l'app est au premier plan
     * @param openBotId   le fil ouvert à l'écran, s'il y en a un
     */
    fun shouldPost(
        trigger: String,
        isLead: Boolean,
        spokenTo: Boolean,
        foreground: Boolean,
        openBotId: String?,
        botId: String,
    ): Boolean {
        if (foreground && openBotId == botId) return false
        return rings(trigger, isLead, spokenTo)
    }
}
