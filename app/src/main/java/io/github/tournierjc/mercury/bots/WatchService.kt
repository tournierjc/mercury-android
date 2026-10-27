package io.github.tournierjc.mercury.bots

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import io.github.tournierjc.mercury.MainActivity
import io.github.tournierjc.mercury.MercuryApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the roster stream alive once the last activity is gone.
 *
 * Without this, `/api/bots/events` lives exactly as long as the app's process: a bot that
 * needs an answer at 3 am waits for the owner to open the app, or for the fifteen-minute
 * periodic check ([AttentionWorker]) to notice it. The stream itself is already process-wide
 * ([BotWatcher] is started by [MercuryApp]); this service is what stops Android from
 * collecting that process, and it is also the honest visible sign of it — a foreground
 * service must show a notification, and that notification says what is being watched.
 *
 * It deliberately does not own the watcher's lifetime: stopping the service stops the promise,
 * not the notifications, so opening the app afterwards behaves exactly as before.
 */
class WatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            AttentionPrefs(this).persistent = false
            stopSelf()
            return START_NOT_STICKY
        }

        val app = application as? MercuryApp
        if (app == null || app.container.serverUrl.isBlank()) {
            // Nothing configured, nothing to watch: a persistent notification promising a
            // watch that cannot exist is worse than no service at all.
            stopSelf()
            return START_NOT_STICKY
        }

        goForeground(WatchSummary.text(0, 0))
        app.container.botWatcher.start()
        observe(app)
        return START_STICKY
    }

    override fun onDestroy() {
        observer?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun observe(app: MercuryApp) {
        observer?.cancel()
        observer = scope.launch {
            app.container.botWatcher.state
                .map { WatchSummary.text(it.bots.size, it.needsYou().size) }
                .distinctUntilChanged()
                .collect { text -> notify(text) }
        }
    }

    private fun goForeground(text: String) {
        ensureChannel()
        val notification = build(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notify(text: String) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        runCatching { manager.notify(NOTIFICATION_ID, build(text)) }
    }

    private fun build(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, WatchService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(WatchSummary.TITLE)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .addAction(Notification.Action.Builder(null, "Arrêter", stop).build())
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Veille des équipiers",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "La connexion au serveur reste ouverte pour vous alerter sans délai."
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "mercury_watch"
        const val NOTIFICATION_ID = 4101
        const val ACTION_STOP = "io.github.tournierjc.mercury.STOP_WATCH"

        /**
         * Ask for the watch. Returns false when the platform refuses the start — Android 12+
         * forbids starting a foreground service from the background, and a dataSync service is
         * not on the exemption list on recent versions. The caller then keeps the periodic
         * check as the fallback rather than pretending the watch is on.
         */
        fun start(context: Context): Boolean = runCatching {
            context.startForegroundService(Intent(context, WatchService::class.java))
            true
        }.getOrDefault(false)

        fun stop(context: Context) {
            context.stopService(Intent(context, WatchService::class.java))
        }
    }
}
