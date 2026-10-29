package io.github.tournierjc.mercury.bots

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Quand un message d'équipier mérite une notification.
 *
 * Le seul cas muet est celui qu'on est en train de lire : notifier un message déjà à l'écran
 * apprend au doigt à ignorer les notifications, et c'est ce réflexe-là qui coûte une réponse
 * urgente.
 */
class BotNotifierGateTest {

    @Test
    fun `a message for the open bot stays silent`() {
        assertFalse(BotNotifier.shouldNotify(foreground = true, openBotId = "scribe", botId = "scribe"))
    }

    @Test
    fun `a message from another bot is announced while the app is open`() {
        assertTrue(BotNotifier.shouldNotify(foreground = true, openBotId = "scribe", botId = "oedipe"))
        assertTrue(BotNotifier.shouldNotify(foreground = true, openBotId = null, botId = "oedipe"))
    }

    @Test
    fun `in the background every message is announced`() {
        assertTrue(BotNotifier.shouldNotify(foreground = false, openBotId = "scribe", botId = "scribe"))
        assertTrue(BotNotifier.shouldNotify(foreground = false, openBotId = null, botId = "scribe"))
    }
}
