package io.github.tournierjc.mercury.bots

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.tournierjc.mercury.MercuryApp
import io.github.tournierjc.mercury.data.BotJson
import java.io.IOException

/**
 * Vérification de fond : demande le roster, annonce ce qui attend une réponse.
 *
 * Sans elle, l'app ne pouvait alerter que process vivant — un équipier qui pose une
 * question à 3 h du matin attendait l'ouverture de l'app. Le SSE reste le chemin
 * temps réel quand l'app tourne ; ceci est le filet pour le reste, cadencé par
 * WorkManager (15 minutes minimum côté Android).
 *
 * Choix : on ne s'appuie que sur `needsYou`, un état qui se suffit à lui-même. Les
 * fins de run exigent une comparaison avec le roster précédent, qu'un process tué
 * n'a plus — [BotJson.currentAttention] les exclut donc volontairement, et les clés
 * déjà vues empêchent de répéter la même alerte à chaque passe.
 */
class AttentionWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = AttentionPrefs(applicationContext)
        if (!prefs.enabled) return Result.success()

        val container = (applicationContext as? MercuryApp)?.container ?: return Result.success()
        if (container.serverUrl.isBlank()) return Result.success()

        val roster = try {
            container.api.bots()
        } catch (e: IOException) {
            // Pas de réseau maintenant : WorkManager réessaiera à la prochaine fenêtre.
            return Result.retry()
        } catch (e: Exception) {
            // Session expirée ou serveur muet : inutile d'insister en boucle.
            return Result.success()
        }

        val memory = container.botNotifyMemory
        // Un « il a besoin de vous » d'un équipier qui n'est pas leader n'est pas une nouvelle
        // pour le propriétaire : son leader est là pour ça. La même règle que pour les messages,
        // sinon le silence des messages serait rompu par les alertes.
        val alerts = BotJson.currentAttention(roster.bots).filter { attention ->
            val bot = roster.bots.firstOrNull { BotJson.id(it) == attention.botId }
            BotNotifyPolicy.rings(isLead = BotJson.isLead(bot), spokenTo = memory.spokenTo(attention.botId))
        }
        val keys = alerts.associate { BotJson.attentionKey(it) to it }
        val seen = prefs.seenKeys

        keys.filterKeys { it !in seen }.values.forEach { container.botNotifier.notify(it) }

        // On ne garde que les clés encore vraies : une question répondue disparaît du
        // roster, et pourra de nouveau alerter si elle revient.
        prefs.seenKeys = keys.keys
        return Result.success()
    }

    companion object {
        const val NAME = "mercury-attention"
    }
}
