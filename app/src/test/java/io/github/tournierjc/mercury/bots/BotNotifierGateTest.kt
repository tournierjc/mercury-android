package io.github.tournierjc.mercury.bots

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Qui a le droit de sonner.
 *
 * Deux règles se composent. La première : ce qu'on est en train de lire ne sonne pas — notifier un
 * message déjà à l'écran apprend au doigt à ignorer les notifications, et c'est ce réflexe-là qui
 * coûte une réponse urgente.
 *
 * La seconde ne porte plus sur le bot mais sur **ce qui a produit son tour** : une réponse à un
 * message de l'owner, et le résultat d'une routine planifiée, sonnent toujours ; un tour de travail
 * venu d'une flotte ne sonne que par son leader. La règle d'avant — « lui ai-je déjà parlé ? » — ne se
 * périmait jamais : un équipier à qui l'owner avait écrit une fois sonnait ensuite pour toutes les
 * tâches qu'on lui confiait, et six équipiers faisaient six sonneries pour une seule nouvelle.
 */
class BotNotifierGateTest {

    private fun post(
        trigger: String = "",
        isLead: Boolean = false,
        spokenTo: Boolean = false,
        foreground: Boolean = false,
        openBotId: String? = null,
        botId: String = "scribe",
    ) = BotNotifyPolicy.shouldPost(
        trigger = trigger,
        isLead = isLead,
        spokenTo = spokenTo,
        foreground = foreground,
        openBotId = openBotId,
        botId = botId,
    )

    @Test
    fun `a message for the open bot stays silent`() {
        assertFalse(post(trigger = "chat", isLead = true, foreground = true, openBotId = "scribe", botId = "scribe"))
        assertFalse(post(trigger = "cron", isLead = true, foreground = true, openBotId = "scribe", botId = "scribe"))
    }

    @Test
    fun `a lead is announced while the app is open, unless it is the open thread`() {
        assertTrue(post(trigger = "mailbox", isLead = true, foreground = true, openBotId = "oedipe", botId = "scribe"))
        assertTrue(post(trigger = "mailbox", isLead = true, foreground = true, openBotId = null, botId = "scribe"))
    }

    @Test
    fun `a lead is announced in the background`() {
        assertTrue(post(trigger = "mailbox", isLead = true, foreground = false, openBotId = "scribe", botId = "scribe"))
        assertTrue(post(trigger = "api", isLead = true, foreground = false))
    }

    @Test
    fun `an answer to the owner always rings, whoever the bot is`() {
        // C'est la conversation qu'il a ouverte : la réponse est ce qu'il attend.
        for (lead in listOf(true, false)) {
            for (spoken in listOf(true, false)) {
                assertTrue("lead=$lead spoken=$spoken", post(trigger = "chat", isLead = lead, spokenTo = spoken))
            }
        }
    }

    @Test
    fun `a scheduled result always rings, whoever the bot is`() {
        // Une routine a rendu un livrable : le propriétaire l'a demandé à l'avance.
        for (lead in listOf(true, false)) {
            assertTrue(
                "lead=$lead",
                post(trigger = "cron", isLead = lead, spokenTo = false, foreground = true, openBotId = "oedipe"),
            )
        }
    }

    @Test
    fun `a crew member's work turn stays silent, even after the owner spoke to it once`() {
        // Le cas rapporté : les six équipiers d'une flotte sonnaient chacun à la fin de leur tâche.
        assertFalse(post(trigger = "mailbox", isLead = false, spokenTo = true, foreground = false))
        assertFalse(post(trigger = "mailbox", isLead = false, spokenTo = true, foreground = true, openBotId = "oedipe"))
        assertFalse(post(trigger = "api", isLead = false, spokenTo = true))
        assertFalse(post(trigger = "mailbox", isLead = false, spokenTo = false))
    }

    @Test
    fun `an unknown origin falls back to the old rule instead of silencing news`() {
        // Daemon antérieur au champ : on ne fait pas taire ce qu'on ne sait pas classer.
        assertTrue(post(trigger = "", isLead = true, spokenTo = false))
        assertTrue(post(trigger = "", isLead = false, spokenTo = true))
        assertFalse(post(trigger = "", isLead = false, spokenTo = false))
    }
}
