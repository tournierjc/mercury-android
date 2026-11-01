package io.github.tournierjc.mercury.device

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import fi.iki.elonen.NanoHTTPD
import io.github.tournierjc.mercury.MainActivity
import io.github.tournierjc.mercury.R
import io.github.tournierjc.mercury.SecurePrefs

/**
 * Service de premier plan qui maintient le pont HTTP ouvert.
 *
 * Android tue les sockets d'une app en arrière-plan : le pont doit donc être un
 * foreground service (type `dataSync`), avec une notification visible et
 * arrêtable par l'utilisateur.
 */
class BridgeService : Service() {

    private var server: PhoneBridgeServer? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        startServer()
        return START_STICKY
    }

    private fun startServer() {
        server?.stop()
        // Keystore unusable : le token vivrait en clair sur le disque. Le pont ne sert
        // rien dans cet état — l'écran Pont l'annonce, au lieu de dégrader en silence.
        if (SecurePrefs.degraded) {
            BridgePrefs(this).enabled = false
            server = null
            return
        }
        val prefs = BridgePrefs(this)
        prefs.enabled = true
        val readers = DeviceReaders(this)
        server = PhoneBridgeServer(prefs, readers, BridgeAddress.localAddress(), prefs.port).also { candidate ->
            runCatching { candidate.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true) }
                .onFailure { server = null }
        }
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        BridgePrefs(this).enabled = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.bridge_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.bridge_channel_description)
            },
        )
    }

    private fun buildNotification(): android.app.Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, BridgeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.bridge_notification_title))
            .setContentText(getString(R.string.bridge_notification_text, BridgePrefs(this).port))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.bridge_notification_stop), stop)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "mercury_bridge"
        private const val NOTIFICATION_ID = 4211
        private const val ACTION_STOP = "io.github.tournierjc.mercury.STOP_BRIDGE"

        fun start(context: Context) {
            val intent = Intent(context, BridgeService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, BridgeService::class.java).setAction(ACTION_STOP))
            BridgePrefs(context).enabled = false
        }
    }
}
