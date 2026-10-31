package io.github.tournierjc.mercury.bots

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Qui a le droit de sonner.
 *
 * Deux règles se composent. La première : ce qu'on est en train de lire ne sonne pas — notifier un
 * message déjà à l'écran apprend au doigt à ignorer les notifications, et c'est ce réflexe-là qui
 * coûte une réponse urgente. La seconde : un job de flotte fait un tour par équipier, donc seul le
 * leader sonne — sauf pour un bot auquel le propriétaire a parlé, parce qu'une conversation est un
 * abonnement.
 */
class BotNotifierGateTest {

    private fun post(
        isLead: Boolean = false,
        spokenTo: Boolean = false,
        foreground: Boolean = false,
        openBotId: String? = null,
        botId: String = "scribe",
    ) = BotNotifyPolicy.shouldPost(
        isLead = isLead,
        spokenTo = spokenTo,
        foreground = foreground,
        openBotId = openBotId,
        botId = botId,
    )

    @Test
    fun `a message for the open bot stays silent`() {
        assertFalse(post(isLead = true, foreground = true, openBotId = "scribe", botId = "scribe"))
    }

    @Test
    fun `a lead is announced while the app is open, unless it is the open thread`() {
        assertTrue(post(isLead = true, foreground = true, openBotId = "oedipe", botId = "scribe"))
        assertTrue(post(isLead = true, foreground = true, openBotId = null, botId = "scribe"))
    }

    @Test
    fun `a lead is announced in the background`() {
        assertTrue(post(isLead = true, foreground = false, openBotId = "scribe", botId = "scribe"))
        assertTrue(post(isLead = true, foreground = false))
    }

    @Test
    fun `a crew member the owner never addressed stays silent`() {
        // Son leader est là pour ça : six tours de flotte, c'est la même nouvelle six fois.
        assertFalse(post(isLead = false, spokenTo = false, foreground = false))
        assertFalse(post(isLead = false, spokenTo = false, foreground = true, openBotId = "oedipe"))
    }

    @Test
    fun `a crew member the owner spoke to is announced`() {
        assertTrue(post(isLead = false, spokenTo = true, foreground = false))
        assertTrue(post(isLead = false, spokenTo = true, foreground = true, openBotId = "oedipe"))
        assertFalse(post(isLead = false, spokenTo = true, foreground = true, openBotId = "scribe", botId = "scribe"))
    }
}
