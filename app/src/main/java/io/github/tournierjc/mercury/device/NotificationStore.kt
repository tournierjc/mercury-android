package io.github.tournierjc.mercury.device

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Messages reçus captés par l'accès notifications.
 *
 * C'est le seul moyen de voir les messages d'apps sans base lisible (WhatsApp,
 * Signal, Messenger…) : Android n'expose pas d'API de lecture pour elles, mais
 * une notification postée est lisible si l'utilisateur a accordé l'accès.
 *
 * Stockage : en mémoire seulement, borné, jamais écrit sur disque.
 */
object NotificationStore {

    private const val MAX_ENTRIES = 200
    private val messages = ArrayDeque<PhoneMessage>()

    fun record(message: PhoneMessage) {
        synchronized(messages) {
            messages.addFirst(message)
            while (messages.size > MAX_ENTRIES) messages.removeLast()
        }
    }

    fun snapshot(limit: Int): List<PhoneMessage> = synchronized(messages) { messages.take(limit) }

    fun clear() = synchronized(messages) { messages.clear() }

    fun isAccessGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun toMessage(sbn: StatusBarNotification): PhoneMessage? {
        val notification = sbn.notification ?: return null
        val extras = notification.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val body = when {
            text.isNotBlank() -> text
            lines != null && lines.isNotEmpty() -> lines.joinToString("\n")
            else -> ""
        }
        if (title.isBlank() && body.isBlank()) return null
        val sender = when {
            extras.containsKey("android.messagingUser") -> title
            title.isNotBlank() -> title
            else -> sbn.packageName
        }
        return PhoneMessage(
            id = "${sbn.key}:${sbn.postTime}",
            source = "notification",
            app = sbn.packageName,
            sender = sender,
            text = body,
            timestamp = OffsetDateTime.ofInstant(Instant.ofEpochMilli(sbn.postTime), ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        )
    }
}

class MercuryNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val statusBarNotification = sbn ?: return
        if (statusBarNotification.packageName == packageName) return
        if (statusBarNotification.isOngoing) return
        NotificationStore.toMessage(statusBarNotification)?.let(NotificationStore::record)
    }
}
