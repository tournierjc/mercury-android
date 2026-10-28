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
import io.github.tournierjc.mercury.data.Notice
import io.github.tournierjc.mercury.data.NoticeKey
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.ui.noticeText

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
            context.getString(R.string.notification_channel_crew),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_crew_desc)
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
            .setContentTitle(noticeText(context, titleNotice(attention)))
            .setContentText(noticeText(context, bodyNotice(attention)))
            .setStyle(Notification.BigTextStyle().bigText(noticeText(context, bodyNotice(attention))))
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

        fun titleNotice(attention: BotAttention): Notice = when (attention.kind) {
            BotAttention.Kind.NeedsYou -> Notice(NoticeKey.ATTENTION_NEEDS_YOU, args = listOf(attention.botName))
            BotAttention.Kind.RunFinished -> Notice(NoticeKey.ATTENTION_FINISHED, args = listOf(attention.botName))
            BotAttention.Kind.RunFailed -> Notice(NoticeKey.ATTENTION_FAILED, args = listOf(attention.botName))
        }

        /**
         * The line under the title. A detail from the daemon is passed through verbatim (it is the
         * teammate's own words); otherwise the kind's own sentence is used.
         */
        fun bodyNotice(attention: BotAttention): Notice {
            val key = when (attention.kind) {
                BotAttention.Kind.NeedsYou -> NoticeKey.ATTENTION_NEEDS_YOU_BODY
                BotAttention.Kind.RunFinished -> NoticeKey.ATTENTION_FINISHED_BODY
                BotAttention.Kind.RunFailed -> NoticeKey.ATTENTION_FAILED_BODY
            }
            return if (attention.detail.isBlank()) Notice(key) else Notice(key).withDetail(attention.detail)
        }
    }
}
