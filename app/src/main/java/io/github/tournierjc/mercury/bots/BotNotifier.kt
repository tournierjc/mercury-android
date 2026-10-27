package io.github.tournierjc.mercury.bots

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import io.github.tournierjc.mercury.MainActivity
import io.github.tournierjc.mercury.data.BotAttention

/**
 * System notifications for bot attention. The app posts them while its process is alive:
 * there is no background service holding the SSE stream, so killing the app stops them.
 *
 * A notification is raised once per state transition (see [BotAttention]) — never on every
 * roster refresh, which would turn the shade into noise.
 */
class BotNotifier(private val context: Context) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Équipiers",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Un équipier a besoin de vous, ou a terminé son travail."
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    /** True when the app may post (always true before Android 13). */
    fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notify(attention: BotAttention) {
        if (!canNotify()) return
        ensureChannel()
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // SINGLE_TOP, not a fresh task: without it the tap recreated the activity, which rebuilt
        // the whole screen graph and showed the Mercury conversation before the bot's own.
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_BOT_ID, attention.botId)
        }
        val pending = PendingIntent.getActivity(
            context,
            attention.botId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title(attention))
            .setContentText(body(attention))
            .setStyle(Notification.BigTextStyle().bigText(body(attention)))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID + attention.botId.hashCode(), notification) }
    }

    fun notice(message: String) {
        if (!canNotify()) return
        ensureChannel()
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Mercury")
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID + message.hashCode(), notification) }
    }

    companion object {
        const val CHANNEL_ID = "bot_attention"
        const val EXTRA_BOT_ID = "botId"
        private const val NOTIFICATION_ID = 4200

        fun title(attention: BotAttention): String = when (attention.kind) {
            BotAttention.Kind.NeedsYou -> "${attention.botName} a besoin de vous"
            BotAttention.Kind.RunFinished -> "${attention.botName} a terminé"
            BotAttention.Kind.RunFailed -> "${attention.botName} a échoué"
        }

        fun body(attention: BotAttention): String = when (attention.kind) {
            BotAttention.Kind.NeedsYou ->
                attention.detail.ifBlank { "Il attend une réponse avant de continuer." }
            BotAttention.Kind.RunFinished ->
                attention.detail.ifBlank { "Le travail est prêt dans ses livrables." }
            BotAttention.Kind.RunFailed ->
                attention.detail.ifBlank { "Le dernier passage s'est arrêté sur une erreur." }
        }
    }
}
